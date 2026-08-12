#!/usr/bin/env node

// Some packages point `types` at a `.d.cts` file and give their `exports` map no `types`
// condition. ScalablyTyped then can't find any typings and silently skips the package, which
// leaves every reference to it as `js.Any` in the facades that depend on it (with a
// "Couldn't qualify" warning). Retargeting `types` at the `.d.ts` the package already ships is
// enough for ST to pick it up.
//
// Usage: retarget-types.js <npm-package> <types-path> [<npm-package> <types-path>]...

const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
if (args.length === 0 || args.length % 2 !== 0) {
  console.error("Usage: retarget-types.js <npm-package> <types-path> [...]");
  process.exit(1);
}

for (let i = 0; i < args.length; i += 2) {
  const pkg = args[i];
  const types = args[i + 1];

  const dir = path.resolve("node_modules", pkg);
  const manifestPath = path.join(dir, "package.json");
  if (!fs.existsSync(manifestPath)) {
    console.error(`retarget-types: ${pkg} is not installed`);
    process.exit(1);
  }
  if (!fs.existsSync(path.join(dir, types))) {
    console.error(`retarget-types: ${pkg} does not ship ${types}`);
    process.exit(1);
  }

  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  manifest.types = types;

  // A `types` condition has to come first to win over `import`/`require`.
  const root = manifest.exports?.["."];
  if (root && typeof root === "object" && !Array.isArray(root) && !root.types) {
    manifest.exports["."] = { types, ...root };
  }

  fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
  console.log(`retarget-types: ${pkg} types -> ${types}`);
}
