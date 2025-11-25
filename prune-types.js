#!/usr/bin/env node

// Vibe coded 🎉 with GPT-5.1

const { Project, SyntaxKind } = require("ts-morph");
const fs = require("fs");
const { minimatch } = require("minimatch");

// ---------------- CLI -----------------

let removedTypesRaw = [];        // strings or patterns
let removedTypes = new Set();    // exact names
let patterns = [];
let verbose = false;

const args = process.argv.slice(2);

for (let i = 0; i < args.length; i++) {
  const a = args[i];

  if (a === "--types") {
    removedTypesRaw = args[++i].split(",").map(s => s.trim()).filter(Boolean);
    removedTypes = new Set(removedTypesRaw);
    continue;
  }

  if (a === "--types-file") {
    const file = args[++i];
    removedTypesRaw = fs.readFileSync(file, "utf8")
      .split(/\r?\n/)
      .map(x => x.trim())
      .filter(Boolean);

    removedTypes = new Set(removedTypesRaw);
    continue;
  }

  if (a === "--verbose") {
    verbose = true;
    continue;
  }

  // remaining args are input file patterns
  patterns.push(a);
}

if (removedTypesRaw.length === 0) {
  console.error("ERROR: No types provided. Use --types or --types-file.");
  process.exit(1);
}

if (patterns.length === 0) {
  console.error("ERROR: No file patterns provided.");
  process.exit(1);
}

function log(...msg) {
  if (verbose) console.log(...msg);
}

console.log("[prune-types] Removing:", removedTypesRaw.join(", "));
console.log("[prune-types] Patterns:", patterns.join(", "));

// ---------------- Wildcard matching -----------------

function matchesRemoved(name) {
  if (!name) return false;

  // exact match
  if (removedTypes.has(name)) return true;

  // match wildcard patterns
  for (const pat of removedTypesRaw) {
    if (minimatch(name, pat)) return true;
  }
  return false;
}

// Extract ALL type identifiers from a TypeNode
function* findReferencedTypeNames(node) {
  if (!node) return;

  // Foo
  if (node.getKind() === SyntaxKind.Identifier) {
    yield node.getText();
  }

  // Foo.Bar.Baz
  if (node.getKind() === SyntaxKind.QualifiedName) {
    yield node.getRight().getText();             // Bar
    yield node.getText();                        // Foo.Bar
  }

  // e.g. extends Foo<T>
  if (node.getKind() === SyntaxKind.ExpressionWithTypeArguments) {
    const expr = node.getExpression();
    yield expr.getText();
  }

  // Recurse
  for (const child of node.getChildren()) {
    yield* findReferencedTypeNames(child);
  }
}

// ---------------- Project -----------------

const project = new Project({
  skipAddingFilesFromTsConfig: true,
  compilerOptions: {
    skipLibCheck: true,
  },
});

const sourceFiles = project.addSourceFilesAtPaths(patterns);
console.log("[prune-types] Loaded", sourceFiles.length, "files");

// ---------------- Helpers -----------------

function shrinkUnionString(str, removedMatcher) {
  const parts = str.split("|").map(s => s.trim());
  const kept = parts.filter(p => !removedMatcher(p));
  if (kept.length === 0) return null;
  if (kept.length === 1) return kept[0];
  return kept.join(" | ");
}

// ---------------- Transform -----------------

let changedFiles = 0;
let removedInterfaces = 0;
let removedProperties = 0;

for (const sf of sourceFiles) {
  console.log("→ Processing:", sf.getBaseName());
  let fileChanged = false;

  for (const iface of sf.getInterfaces()) {
    const ifaceName = iface.getName();

    if (matchesRemoved(ifaceName)) {
      console.log("    Removing interface", ifaceName);
      iface.remove();
      removedInterfaces++;
      fileChanged = true;
      continue;
    }

    for (const prop of iface.getProperties()) {
      const typeNode = prop.getTypeNode();
      if (!typeNode) continue;

      // ---------- NEW: match ANY referenced type ----------
      let removeThisProp = false;
      for (const ref of findReferencedTypeNames(typeNode)) {
        if (matchesRemoved(ref)) {
          console.log(`    Removing property ${prop.getName()} (type contains ${ref})`);
          removeThisProp = true;
          break;
        }
      }

      if (removeThisProp) {
        prop.remove();
        removedProperties++;
        fileChanged = true;
        continue;
      }

      // existing union shrink logic still works:
      if (typeNode.getKind() === SyntaxKind.UnionType) {
        const unionStr = typeNode.getText();
        const newStr = shrinkUnionString(unionStr, matchesRemoved);
        if (!newStr) {
          console.log(`    Removing property ${prop.getName()} (union → empty)`);
          prop.remove();
          removedProperties++;
          fileChanged = true;
        } else if (newStr !== unionStr) {
          typeNode.replaceWithText(newStr);
          fileChanged = true;
        }
      }
    }
  }

  if (fileChanged) changedFiles++;
}

// ---------------- Save -----------------

console.log("[prune-types] Saving changes...");

(async () => {
  await project.save();
  console.log("[prune-types] Done.");
  console.log("  Changed files:", changedFiles);
  console.log("  Removed interfaces:", removedInterfaces);
  console.log("  Removed properties:", removedProperties);
})();
