#!/usr/bin/env node

// Vibe coded 🎉 with GPT-5.1

const fs = require("fs");
const path = require("path");
const { minimatch } = require("minimatch");

if (process.argv.length < 4) {
  console.error("Usage: node script.js <directory> <patterns.txt>");
  process.exit(1);
}

const baseDir = path.resolve(process.argv[2]);
const patternsFile = path.resolve(process.argv[3]);

// Load patterns
const patterns = fs.readFileSync(patternsFile, "utf8")
  .split(/\r?\n/)
  .map(x => x.trim())
  .filter(Boolean);

// Match a file's *relative* path against patterns
function matchesAnyPattern(fullPath) {
  const rel = path.relative(baseDir, fullPath);
  return patterns.some(p => minimatch(rel, p, { dot: true }));
}

/**
 * Recursively walk a directory.
 * Deletes:
 *   - files that don't match
 *   - directories that become empty
 * Returns true if the directory (or file) contains at least one matching file.
 */
function processPath(fullPath) {
  const stat = fs.statSync(fullPath);

  if (stat.isFile()) {
    if (matchesAnyPattern(fullPath)) {
      return true; // keep this file
    } else {
      fs.unlinkSync(fullPath);
      return false; // deleted
    }
  }

  if (stat.isDirectory()) {
    let keepSomething = false;
    const entries = fs.readdirSync(fullPath);

    for (const entry of entries) {
      const full = path.join(fullPath, entry);
      const keep = processPath(full);
      if (keep) keepSomething = true;
    }

    // If directory contains nothing worth keeping → delete it
    if (!keepSomething) {
      fs.rmdirSync(fullPath);
      return false;
    }

    return true;
  }

  // Ignore other filesystem types
  return false;
}

processPath(baseDir);
