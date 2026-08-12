#!/usr/bin/env node

// ScalablyTyped generates one `@JSImport` per TypeScript source file, so a package whose
// `package.json` `exports` map only publishes a root and a few named subpaths ends up with
// facade imports that no strict resolver will load — Node throws ERR_PACKAGE_PATH_NOT_EXPORTED
// for every deep `./dist/<file>` path. Scala.js only emits the `require` when a module's values
// are reachable, so the breakage is latent: bundlers resolve the deep paths from disk and it
// only surfaces under Node SSR or a production resolver.
//
// This rewrites each deep import to an entry point the package actually exports, choosing per
// path so that every symbol the facade imports from it is still reachable. Rewriting everything
// to the package root would be wrong: `@tanstack/table-core` publishes 109 names at the root and
// another 285 only under `./static-functions`, and pointing the latter at the root would turn a
// loud resolver error into a silent `undefined`.
//
// A deep module whose symbols the package exports from nowhere at all cannot be remapped — the
// facade is exposing internals. Those are listed in the --allowed file so they stay visible as
// warnings instead of failing every generation; anything not listed is a hard error, which is
// what keeps a new occurrence from shipping.
//
// Usage: remap-exports.js [--allowed <file>] <generated-sources-dir> <npm-package>...

const fs = require("fs");
const path = require("path");
const { pathToFileURL } = require("url");

const args = process.argv.slice(2);

let allowed = new Set();
const allowedIdx = args.indexOf("--allowed");
if (allowedIdx !== -1) {
  const file = args[allowedIdx + 1];
  args.splice(allowedIdx, 2);
  if (fs.existsSync(file)) {
    allowed = new Set(
      fs.readFileSync(file, "utf8")
        .split(/\r?\n/)
        .map(l => l.replace(/#.*$/, "").trim())
        .filter(Boolean)
    );
  }
}

if (args.length < 2) {
  console.error("Usage: remap-exports.js [--allowed <file>] <generated-sources-dir> <npm-package>...");
  process.exit(1);
}

const [sourcesDir, ...packages] = args;

const scalaFiles = [];
(function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full);
    else if (entry.name.endsWith(".scala")) scalaFiles.push(full);
  }
})(sourcesDir);

const sources = new Map(scalaFiles.map(f => [f, fs.readFileSync(f, "utf8")]));

const escape = s => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

// The names an ES module exports, loaded by file path so the `exports` map can't refuse us.
async function exportsOf(file) {
  try {
    return new Set(Object.keys(await import(pathToFileURL(file).href)));
  } catch {
    return null;
  }
}

// Entry points the package publishes: { specifier, file }.
function entryPoints(pkg) {
  const pkgDir = path.resolve("node_modules", pkg);
  const map = JSON.parse(fs.readFileSync(path.join(pkgDir, "package.json"), "utf8")).exports ?? {};
  const out = [];

  for (const [subpath, target] of Object.entries(map)) {
    // A target is either a string or a conditions object; we want the ESM entry.
    const file = typeof target === "string" ? target : (target.import ?? target.default);
    if (typeof file !== "string" || !file.endsWith(".js")) continue;

    out.push({
      specifier: subpath === "." ? pkg : `${pkg}/${subpath.replace(/^\.\//, "")}`,
      file: path.join(pkgDir, file)
    });
  }
  return out;
}

// Symbols the facade pulls out of `deep`: the names in `@JSImport(deep, "Name")`, plus the
// dynamic-access names in any file that imports it as a namespace. A dotted name such as
// `filterFns.arrHas` is member access on an exported object, so only the head must be exported.
function requiredSymbols(deep) {
  const named = new RegExp(`@JSImport\\("${escape(deep)}",\\s*"([^"]+)"\\)`, "g");
  const namespaced = new RegExp(`@JSImport\\("${escape(deep)}",\\s*JSImport\\.Namespace\\)`);
  const dynamic = /(?:applyDynamic|selectDynamic|updateDynamic)\("([^"]+)"\)/g;

  const symbols = new Set();
  for (const src of sources.values()) {
    if (!src.includes(`"${deep}"`)) continue;
    for (const m of src.matchAll(named)) symbols.add(m[1].split(".")[0]);
    if (namespaced.test(src)) for (const m of src.matchAll(dynamic)) symbols.add(m[1].split(".")[0]);
  }
  return symbols;
}

async function planFor(pkg) {
  const deepPrefix = `${pkg}/dist/`;
  const deepRe = new RegExp(`@JSImport\\("(${escape(deepPrefix)}[^"]+)"`, "g");

  const deepSpecifiers = new Set();
  for (const src of sources.values()) {
    for (const m of src.matchAll(deepRe)) deepSpecifiers.add(m[1]);
  }
  if (deepSpecifiers.size === 0) return { plan: new Map(), orphans: [] };

  const entries = [];
  for (const e of entryPoints(pkg)) {
    const names = await exportsOf(e.file);
    if (names) entries.push({ ...e, names });
  }

  // Root first, then the named subpaths — the root is the stable, documented surface, so we only
  // reach for a subpath when the root doesn't carry the symbols.
  entries.sort((a, b) =>
    a.specifier === pkg ? -1 : b.specifier === pkg ? 1 : a.specifier.localeCompare(b.specifier)
  );

  const plan = new Map();
  const orphans = [];

  for (const deep of [...deepSpecifiers].sort()) {
    const rel = deep.slice(deepPrefix.length);
    const deepFile = path.resolve("node_modules", pkg, "dist", `${rel}.js`);
    const deepExports = await exportsOf(deepFile);

    // Symbols the deep module doesn't itself export are already broken in the generated facade
    // (ST emits call helpers on nested objects that match no export); a path remap can't fix
    // those, so they don't get to veto an entry point.
    const wanted = [...requiredSymbols(deep)].filter(s => !deepExports || deepExports.has(s));

    // Prefer the subpath that publishes this exact file — that is the package's own name for it.
    const exact = entries.find(e => e.file === deepFile);
    const covering = exact ?? entries.find(e => wanted.every(s => e.names.has(s)));

    if (!covering) {
      orphans.push({ deep, symbols: wanted.filter(s => !entries.some(e => e.names.has(s))) });
      continue;
    }
    plan.set(deep, covering.specifier);
  }

  return { plan, orphans };
}

async function main() {
  const plan = new Map();
  let failed = false;

  for (const pkg of packages) {
    const { plan: pkgPlan, orphans } = await planFor(pkg);
    if (pkgPlan.size === 0 && orphans.length === 0) continue;

    for (const [deep, target] of pkgPlan) plan.set(deep, target);

    const targets = new Map();
    for (const target of pkgPlan.values()) targets.set(target, (targets.get(target) ?? 0) + 1);
    console.log(`remap-exports: ${pkg} — ${pkgPlan.size} deep path(s) remapped`);
    for (const [target, n] of [...targets].sort((a, b) => b[1] - a[1])) {
      console.log(`    ${String(n).padStart(3)} -> ${target}`);
    }

    for (const { deep, symbols } of orphans) {
      if (allowed.has(deep)) {
        console.log(`    !   ${deep} left as is (unexported internals: ${symbols.join(", ")})`);
      } else {
        failed = true;
        console.error(`  ✗ ${deep}\n      no exported entry point carries: ${symbols.join(", ")}`);
      }
    }
  }

  let rewritten = 0;
  for (const [file, before] of sources) {
    let after = before;
    for (const [deep, target] of plan) {
      after = after.split(`@JSImport("${deep}"`).join(`@JSImport("${target}"`);
    }
    if (after !== before) {
      fs.writeFileSync(file, after);
      rewritten += 1;
    }
  }
  console.log(`remap-exports: rewrote ${rewritten} file(s)`);

  // Guard: nothing unaccounted for may still point inside dist/, or we ship a latent crash.
  const deepRe = /@JSImport\("(@?[^"]*?\/dist\/[^"]+)"/g;
  const stragglers = new Set();
  for (const file of sources.keys()) {
    for (const m of fs.readFileSync(file, "utf8").matchAll(deepRe)) {
      const spec = m[1];
      if (packages.some(p => spec.startsWith(`${p}/dist/`)) && !allowed.has(spec)) {
        stragglers.add(spec);
      }
    }
  }
  if (stragglers.size) {
    failed = true;
    console.error(`  ✗ still importing unexported deep paths: ${[...stragglers].join(", ")}`);
  }

  if (failed) process.exit(1);
}

main();
