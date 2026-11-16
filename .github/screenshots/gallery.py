#!/usr/bin/env python3
"""Write a browsable HTML gallery of JVM screen renders and optionally fail if any are missing.

The PNGs are recordings of the production Compose screens. This does not compare
them to a previous baseline.
"""

from __future__ import annotations

import argparse
import html
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_FILE = Path(__file__).with_name("expected.txt")
DEFAULT_DIR = ROOT / "app" / "build" / "outputs" / "screenshots"
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
MIN_BYTES = 4096


def expected_names() -> list[str]:
    names: list[str] = []
    for raw in EXPECTED_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#"):
            names.append(line)
    if not names:
        raise SystemExit(f"No screen names in {EXPECTED_FILE}")
    return names


def output_dir() -> Path:
    override = os.environ.get("SERIFU_SCREENSHOT_DIR")
    return Path(override) if override else DEFAULT_DIR


def valid_png(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < MIN_BYTES:
        return False
    return path.read_bytes()[:8] == PNG_MAGIC


def screen_id(name: str) -> str:
    for theme in ("-light", "-dark"):
        if name.endswith(theme):
            return name[: -len(theme)]
    return name


def theme_of(name: str) -> str:
    if name.endswith("-dark"):
        return "dark"
    if name.endswith("-light"):
        return "light"
    return ""


def write_gallery(directory: Path, names: list[str], present: dict[str, Path], missing: list[str]) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    order: list[str] = []
    for name in names:
        sid = screen_id(name)
        if sid not in order:
            order.append(sid)
    sections: list[str] = []
    for sid in order:
        figures: list[str] = []
        for theme in ("light", "dark"):
            key = f"{sid}-{theme}"
            label = html.escape(sid.replace("-", " "))
            if key in present:
                figures.append(
                    "<figure>"
                    f'<img src="{html.escape(key)}.png" alt="{html.escape(label)} {theme}">'
                    f"<figcaption>{html.escape(label)} · {theme}</figcaption>"
                    "</figure>"
                )
            else:
                figures.append(
                    "<figure class=\"missing\">"
                    f"<figcaption>{html.escape(label)} · {theme} missing</figcaption>"
                    "</figure>"
                )
        title = html.escape(sid.replace("-", " "))
        sections.append(f"<section><h2>{title}</h2>{''.join(figures)}</section>")
    note = (
        f"{len(present)} recorded, {len(missing)} missing. "
        "These PNGs are JVM recordings of the production screens, not a regression baseline."
    )
    page = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Serifu screen renders</title>
<style>
  body {{ margin: 0; font: 15px/1.4 system-ui, sans-serif; background: #16140f; color: #f3eee3; }}
  header {{ padding: 28px 24px 8px; max-width: 1100px; margin: 0 auto; }}
  h1 {{ font-size: 28px; font-weight: 650; margin: 0 0 8px; }}
  p {{ margin: 0; color: #c8c0b0; }}
  main {{ max-width: 1100px; margin: 0 auto; padding: 8px 24px 48px; }}
  section {{ margin-top: 28px; }}
  h2 {{ font-size: 18px; font-weight: 650; margin: 0 0 12px; text-transform: capitalize; }}
  section {{ display: block; }}
  figure {{ display: inline-block; margin: 0 16px 16px 0; vertical-align: top; }}
  img {{ width: 280px; height: auto; background: #000; border-radius: 12px; }}
  figcaption {{ margin-top: 6px; color: #c8c0b0; font-size: 13px; }}
  .missing {{ width: 280px; height: 120px; border: 1px dashed #c8322b; border-radius: 12px; }}
</style>
</head>
<body>
<header>
  <h1>Serifu screen renders</h1>
  <p>{html.escape(note)}</p>
</header>
<main>
{''.join(sections)}
</main>
</body>
</html>
"""
    (directory / "index.html").write_text(page, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if any expected PNG is missing or invalid. Does not rewrite the gallery.")
    parser.add_argument("--allow-missing", action="store_true", help="Write the gallery even when PNGs are missing, and exit 0.")
    args = parser.parse_args()
    directory = output_dir()
    names = expected_names()
    present: dict[str, Path] = {}
    missing: list[str] = []
    for name in names:
        path = directory / f"{name}.png"
        if valid_png(path):
            present[name] = path
        else:
            missing.append(name)
    if not args.check:
        write_gallery(directory, names, present, missing)
        print(f"Wrote {directory / 'index.html'}")
    print(f"Screen renders: {len(present)} present, {len(missing)} missing")
    if missing and not args.allow_missing:
        print("Missing or invalid screen renders:", file=sys.stderr)
        for name in missing:
            path = directory / f"{name}.png"
            detail = "absent" if not path.is_file() else f"invalid ({path.stat().st_size} bytes)"
            print(f"  {name}.png ({detail})", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
