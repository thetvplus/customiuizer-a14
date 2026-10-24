#!/usr/bin/env python3
"""Check repository Markdown targets, anchors and images (offline, stdlib only)."""
from __future__ import annotations

import argparse
import html
import re
import subprocess
import sys
import unicodedata
from pathlib import Path
from urllib.parse import unquote, urlsplit

REPO_ROOT = Path(__file__).resolve().parent.parent


def prose(text: str) -> str:
    """Blank fenced code/comments while preserving line numbers."""
    fence = ""
    lines = []
    for line in text.splitlines(keepends=True):
        marker = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if marker and (not fence or marker[1][0] == fence[0] and len(marker[1]) >= len(fence)):
            fence = marker[1] if not fence else ""
            lines.append("\n")
        else:
            lines.append("\n" if fence else line)
    return re.sub(r"<!--.*?-->", lambda m: "\n" * m[0].count("\n"), "".join(lines), flags=re.S)


def anchors(text: str) -> set[str]:
    clean = prose(text)
    result = set(re.findall(r'\b(?:id|name)=["\']([^"\']+)["\']', clean))
    duplicates: dict[str, int] = {}
    lines = clean.splitlines()
    headings = []
    for index, line in enumerate(lines):
        match = re.match(r"^\s{0,3}#{1,6}\s+(.+?)(?:\s+#+)?\s*$", line)
        if match:
            headings.append(match[1])
        elif index and re.fullmatch(r"\s{0,3}(?:=+|-+)\s*", line) and lines[index - 1].strip():
            headings.append(lines[index - 1].strip())
    for heading in headings:
        heading = re.sub(r"\[([^]]+)\]\([^)]*\)", r"\1", heading)
        heading = html.unescape(re.sub(r"<[^>]*>", "", heading)).lower()
        slug = "".join(c for c in heading if c in "_- " or not unicodedata.category(c).startswith(("P", "S")))
        slug = slug.replace(" ", "-")
        count = duplicates.get(slug, 0)
        duplicates[slug] = count + 1
        result.add(f"{slug}-{count}" if count else slug)
    return result


def destinations(text: str):
    clean = prose(text)
    clean = re.sub(r"(`+).*?\1", lambda m: " " * len(m[0]), clean)
    # Inline destinations use a balanced scan so URLs/paths can contain parentheses.
    for match in re.finditer(r"!?\[(?:\\.|[^\]\\])*\]\(", clean):
        start = match.end()
        depth, end = 1, start
        while end < len(clean) and depth:
            if clean[end] == "\\":
                end += 2
                continue
            if clean[end] == "(":
                depth += 1
            elif clean[end] == ")":
                depth -= 1
            end += 1
        if depth:
            raise ValueError("Unclosed Markdown link")
        value = clean[start:end - 1].strip()
        if value.startswith("<"):
            value = value[1:value.index(">")]
        else:
            value = re.split(r'\s+["\']', value, maxsplit=1)[0]
        yield clean.count("\n", 0, match.start()) + 1, re.sub(r"\\([() ])", r"\1", value)
    definitions = {}
    for match in re.finditer(r"(?m)^\s{0,3}\[([^]]+)\]:\s*(<[^>]+>|\S+)", clean):
        definitions[match[1].casefold()] = match[2].strip("<>")
        yield clean.count("\n", 0, match.start()) + 1, definitions[match[1].casefold()]
    for match in re.finditer(r"!?\[([^]\n]+)\]\[([^]\n]*)\]", clean):
        name = (match[2] or match[1]).casefold()
        if name not in definitions:
            raise ValueError(f"Undefined link reference: {name}")
    for match in re.finditer(r'<(?:img|a|source|video)\b[^>]*?\b(?:src|href)=["\']([^"\']+)["\']', clean, re.I):
        yield clean.count("\n", 0, match.start()) + 1, match[1]


def check_file(repo: Path, path: Path, anchor_cache: dict[Path, set[str]]) -> list[str]:
    errors = []
    text = path.read_text(encoding="utf-8")
    try:
        links = list(destinations(text))
    except ValueError as error:
        return [f"{path.relative_to(repo)}: {error}"]
    for line, raw in links:
        url = urlsplit(html.unescape(raw))
        if url.scheme or url.netloc:
            continue
        target = (repo / unquote(url.path).lstrip("/") if url.path.startswith("/") else path.parent / unquote(url.path)).resolve() if url.path else path.resolve()
        label = f"{path.relative_to(repo)}:{line}: {raw}"
        if not target.is_relative_to(repo.resolve()) or not target.exists():
            errors.append(f"{label}: missing repository target")
        elif url.fragment and target.suffix.lower() == ".md":
            if target not in anchor_cache:
                anchor_cache[target] = anchors(target.read_text(encoding="utf-8"))
            if unquote(url.fragment) not in anchor_cache[target]:
                errors.append(f"{label}: missing anchor")
    return errors


def markdown_files(repo: Path) -> list[Path]:
    result = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.md"], cwd=repo)
    return sorted({repo / p for p in result.decode("utf-8").split("\0") if p and (repo / p).is_file()})


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT)
    args = parser.parse_args()
    repo = args.repo_root.resolve()
    paths = markdown_files(repo)
    cache: dict[Path, set[str]] = {}
    errors = [error for path in paths for error in check_file(repo, path, cache)]
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"docs: {len(paths)} Markdown files; local links, anchors and images passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
