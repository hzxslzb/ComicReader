#!/usr/bin/env python3
"""Check local documentation without network access or third-party packages.

Run from any directory: python3 /path/to/ComicReader/Doc/tools/check_docs.py
Checks links (not fragments), fenced blocks, H1 headings, version baseline,
and the uncompressed editable draw.io XML. This is not a Markdown renderer.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "Doc"
ERRORS: list[str] = []
LINK_COUNT = 0


def fail(path: Path, message: str) -> None:
    ERRORS.append(f"{path.relative_to(ROOT)}: {message}")


def prose_without_fences(path: Path, text: str) -> str:
    result: list[str] = []
    fence: tuple[str, int, int] | None = None
    for line_number, line in enumerate(text.splitlines(), 1):
        match = re.match(r"^\s*(`{3,}|~{3,})(.*)$", line)
        if fence:
            if (match and match[1][0] == fence[0]
                    and len(match[1]) >= fence[1] and not match[2].strip()):
                fence = None
            result.append("")
        elif match:
            fence = (match[1][0], len(match[1]), line_number)
            result.append("")
        else:
            result.append(line)
    if fence:
        fail(path, f"unclosed code fence from line {fence[2]}")
    return "\n".join(result)


def check_target(path: Path, target: str) -> None:
    global LINK_COUNT
    # The repository uses ordinary inline links, with optional HTML titles.
    target = target.strip()
    if target.startswith("<"):
        target = target[1:target.find(">")]
    else:
        target = re.split(r'\s+["\']', target, maxsplit=1)[0]
    parsed = urlsplit(target)
    if parsed.scheme or target.startswith("//") or not parsed.path:
        return
    LINK_COUNT += 1
    decoded = unquote(parsed.path)
    if Path(decoded).is_absolute():
        fail(path, f"non-portable absolute link: {target}")
        return
    resolved = (path.parent / decoded).resolve()
    if not resolved.is_relative_to(ROOT):
        fail(path, f"link escapes repository: {target}")
    elif not resolved.exists():
        fail(path, f"missing local link target: {target}")


def check_markdown(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if not text.strip():
        fail(path, "empty document")
    prose = prose_without_fences(path, text)
    if not re.search(r"^#\s+\S", prose, re.MULTILINE) and not re.search(r"<h1\b", prose):
        fail(path, "missing level-one title")
    for target in re.findall(r"!?\[[^\]\n]*\]\(([^)\n]+)\)", prose):
        check_target(path, target)
    for target in re.findall(r"(?:src|href)\s*=\s*[\"']([^\"']+)[\"']", prose):
        check_target(path, target)
    if path.is_relative_to(DOC) and re.search(r"(?:file://|/Users/|/home/)", text):
        fail(path, "contains a machine-specific path")


def check_diagram(path: Path) -> None:
    try:
        tree = ET.parse(path)
    except (OSError, ET.ParseError) as exc:
        fail(path, f"invalid diagram XML: {exc}")
        return
    roots = tree.findall(".//mxGraphModel/root")
    if not roots:
        fail(path, "expected uncompressed mxGraphModel/root")
    for root in roots:
        cells = root.findall("mxCell")
        ids = [cell.get("id") for cell in cells]
        if len(ids) != len(set(ids)) or None in ids:
            fail(path, "missing or duplicate cell IDs")
        if not {"0", "1"}.issubset(set(ids)):
            fail(path, "missing root/layer cells")
        for cell in cells:
            for attr in ("parent", "source", "target"):
                if cell.get(attr) is not None and cell.get(attr) not in ids:
                    fail(path, f"cell {cell.get('id')} has dangling {attr}")
            if cell.get("vertex") == "1":
                geom = cell.find("mxGeometry")
                if geom is None:
                    fail(path, f"vertex {cell.get('id')} has no geometry")
                    continue
                try:
                    if any(float(geom.get(a, "-1")) < 0 for a in ("x", "y")):
                        fail(path, f"vertex {cell.get('id')} has negative/missing coordinates")
                    if any(float(geom.get(a, "0")) <= 0 for a in ("width", "height")):
                        fail(path, f"vertex {cell.get('id')} has invalid dimensions")
                except ValueError:
                    fail(path, f"vertex {cell.get('id')} has nonnumeric geometry")
            if cell.get("edge") == "1":
                geom = cell.find("mxGeometry")
                if geom is None or geom.get("relative") != "1":
                    fail(path, f"edge {cell.get('id')} needs relative geometry")
                if not cell.get("source") or not cell.get("target"):
                    fail(path, f"edge {cell.get('id')} needs endpoints")


def check_baseline() -> None:
    config = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    version = re.search(r"versionName\s+'([^']+)'", config)
    code = re.search(r"versionCode\s+(\d+)", config)
    index = DOC / "README.md"
    if version and code:
        expected = f"{version[1]} / versionCode {code[1]}"
        if expected not in index.read_text(encoding="utf-8"):
            fail(index, f"baseline must match app/build.gradle: {expected}")


def main() -> int:
    files = sorted(set(DOC.rglob("*.md")) | set((ROOT / "docs").rglob("*.md"))
                   | {ROOT / name for name in ("README.md", "TESTING.md", "CHANGELOG.md")})
    for path in files:
        check_markdown(path)
    check_diagram(DOC / "diagrams/architecture.drawio")
    check_baseline()
    if ERRORS:
        print("Documentation checks failed:", file=sys.stderr)
        for error in ERRORS:
            print(f"  - {error}", file=sys.stderr)
        return 1
    doc_files = list(DOC.rglob("*.md"))
    characters = sum(len(path.read_text(encoding="utf-8")) for path in doc_files)
    print(f"PASS: {len(files)} Markdown files, {LINK_COUNT} local links, draw.io XML and version baseline")
    print(f"Doc/: {len(doc_files)} Markdown files, {characters:,} Unicode characters")
    print("Not checked: remote URLs, anchor fragments, full Mermaid syntax, rendered layout or semantic accuracy.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
