"""Verify an extracted frozen package against its inventory and SHA-256 manifest.

This validates bytes and safe paths, not program behavior or host acceptance.
Run on a fresh extracted copy, before generating local reports/build outputs.
"""
import csv
import hashlib
from pathlib import Path, PurePosixPath
import sys

ROOT = Path(__file__).resolve().parents[1]


def safe_path(value):
    path = PurePosixPath(value)
    return (bool(value) and not path.is_absolute() and "\\" not in value
            and ":" not in value and all(p not in ("", ".", "..") for p in value.split("/")))


def main():
    manifest = ROOT / "MANIFEST_SHA256.csv"
    if not manifest.is_file():
        raise ValueError("Manifest absent: run this tool in the extracted final ZIP, not staging.")
    with manifest.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ["relative_path", "bytes", "sha256"]:
            raise ValueError("Unexpected manifest header")
        rows = list(reader)
    names = [row["relative_path"] for row in rows]
    if len({name.casefold() for name in names}) != len(names):
        raise ValueError("Duplicate or case-colliding manifest path")
    if "MANIFEST_SHA256.csv" in names:
        raise ValueError("The manifest must exclude itself")
    for row in rows:
        name = row["relative_path"]
        if not safe_path(name):
            raise ValueError("Unsafe manifest path: " + name)
        file = ROOT / name
        if file.is_symlink() or not file.is_file():
            raise ValueError("Missing or linked file: " + name)
        content = file.read_bytes()
        if len(content) != int(row["bytes"]):
            raise ValueError("Byte count differs: " + name)
        if hashlib.sha256(content).hexdigest() != row["sha256"].lower():
            raise ValueError("SHA-256 differs: " + name)
    actual = set()
    for file in ROOT.rglob("*"):
        if file.is_symlink():
            raise ValueError("Linked path in extracted package")
        if file.is_file():
            actual.add(file.relative_to(ROOT).as_posix())
    if actual != set(names) | {"MANIFEST_SHA256.csv"}:
        raise ValueError("File inventory differs (run on a clean extraction)")
    inventory = (ROOT / "FILES_INCLUDED.txt").read_text(encoding="utf-8").splitlines()
    payload = [name for name in names if name != "FILES_INCLUDED.txt"]
    if inventory != sorted(payload) or len(inventory) != len(set(inventory)):
        raise ValueError("FILES_INCLUDED.txt differs from manifest")
    print(f"Package integrity PASS: {len(actual)} files, all payload hashes match.")
    print("This is an integrity check, not a framework acceptance result.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        print("Package integrity FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
