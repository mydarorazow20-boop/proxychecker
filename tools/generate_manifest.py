#!/usr/bin/env python3
"""Regenerate MANIFEST.md (path, size, SHA-256) for the project tree."""
import hashlib
import os
import sys

root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
skip_dirs = {".git", ".gradle", "build", ".idea"}
rows = []
for dirpath, dirnames, filenames in os.walk(root):
    dirnames[:] = sorted(d for d in dirnames if d not in skip_dirs)
    for name in sorted(filenames):
        full = os.path.join(dirpath, name)
        rel = os.path.relpath(full, root).replace(os.sep, "/")
        if rel == "MANIFEST.md":
            continue
        with open(full, "rb") as fh:
            data = fh.read()
        rows.append((rel, len(data), hashlib.sha256(data).hexdigest()))

lines = [
    "# MANIFEST",
    "",
    "Files in this ZIP (MANIFEST.md itself excluded). Regenerate with `python3 tools/generate_manifest.py`.",
    "",
    f"Total files listed: {len(rows)}",
    "",
    "| Path | Bytes | SHA-256 |",
    "|---|---|---|",
]
for rel, size, digest in rows:
    lines.append(f"| `{rel}` | {size} | `{digest}` |")
with open(os.path.join(root, "MANIFEST.md"), "w", encoding="utf-8") as out:
    out.write("\n".join(lines) + "\n")
print(f"MANIFEST.md written with {len(rows)} entries", file=sys.stdout)
