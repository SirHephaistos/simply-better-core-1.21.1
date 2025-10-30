#!/usr/bin/env python3
from pathlib import Path

output_file = Path("allCrudManagers.txt")
separator = "-" * 100 + "\n"

with output_file.open("w", encoding="utf-8") as out:
    for file in sorted(Path(".").glob("*CrudManager.java"), key=lambda p: p.name.lower()):
        with file.open("r", encoding="utf-8", errors="ignore") as f:
            out.write(f.read())
        out.write("\n" + separator)

print("Fichiers concaténés dans "+ str(output_file))
