#!/usr/bin/env node

// The ScalablyTyped parser (CLI 1.0.0-beta45) does not understand TypeScript 4.7
// variance annotations on type parameters (`interface Foo<in out T> {}`), and fails
// the whole conversion with "Parse error ... '>' expected". The annotations are pure
// checker hints with no effect on the emitted types, so we strip them before running
// the converter. Removing them only makes TS infer variance instead of asserting it.

const fs = require("fs");
const path = require("path");

if (process.argv.length < 3) {
  console.error("Usage: node strip-variance.js <directory>...");
  process.exit(1);
}

// A variance modifier only ever follows `<` or `,` in a type parameter list, and is
// always followed by whitespace then the parameter name. Requiring the trailing
// whitespace keeps identifiers such as `<inFoo>` or `<outer>` from matching.
const VARIANCE = /([<,]\s*)(?:in\s+out|in|out)\s+(?=[A-Za-z_$])/g;

let filesChanged = 0;
let annotationsRemoved = 0;

function processFile(fullPath) {
  const content = fs.readFileSync(fullPath, "utf8");
  const matches = content.match(VARIANCE);
  if (!matches) return;

  fs.writeFileSync(fullPath, content.replace(VARIANCE, "$1"));
  filesChanged += 1;
  annotationsRemoved += matches.length;
}

function processPath(fullPath) {
  const stat = fs.statSync(fullPath);

  if (stat.isDirectory()) {
    for (const entry of fs.readdirSync(fullPath)) {
      processPath(path.join(fullPath, entry));
    }
  } else if (stat.isFile() && fullPath.endsWith(".d.ts")) {
    processFile(fullPath);
  }
}

for (const arg of process.argv.slice(2)) {
  const dir = path.resolve(arg);
  if (!fs.existsSync(dir)) {
    console.error(`No such directory: ${dir}`);
    process.exit(1);
  }
  processPath(dir);
}

console.log(
  `strip-variance: removed ${annotationsRemoved} variance annotation(s) from ${filesChanged} file(s)`
);
