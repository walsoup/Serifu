#!/usr/bin/env python3
"""Rewrites the examples block in README.md from the example sources and screenshots."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src/debug/java/com/walsoup/linen/examples"
README = ROOT / "README.md"
START, END = "<!-- examples:start -->", "<!-- examples:end -->"

GROUPS = [
    ("Screens", [
        ("list", "List", "ListScreenExample"),
        ("detail", "Detail", "NoteDetailExample"),
        ("settings", "Settings", "SettingsScreenExample"),
        ("search", "Search", "SearchExample"),
        ("editor", "Editor", "EditorExample"),
        ("onboarding", "Onboarding", "OnboardingExample"),
    ]),
    ("Recording", [
        ("live", "Live recording", "LiveRecordingExample"),
        ("review", "Review sheet", "ReviewSheetExample"),
    ]),
    ("Overlays", [
        ("dialog", "Confirm dialog", "ConfirmDialogExample"),
        ("sheet", "Picker sheet", "PickerSheetExample"),
        ("snackbar", "Snackbar", "SnackbarExample"),
    ]),
    ("Tokens", [
        ("controls", "Controls", "ControlsExample"),
        ("colors", "Colors", "ColorsExample"),
        ("type", "Type", "TypeExample"),
    ]),
]


def find_example(name):
    for path in sorted(SRC.glob("*.kt")):
        text = path.read_text()
        m = re.search(r"(?:/\*\*((?:(?!\*/).)*)\*/\s*)?(@Composable\s+fun " + name + r"\(\))", text, re.S)
        if not m:
            continue
        doc = " ".join(line.strip(" *") for line in (m.group(1) or "").splitlines()).strip()
        start = m.start(2)
        depth = 0
        for j in range(text.index("{", m.end(2)), len(text)):
            depth += {"{": 1, "}": -1}.get(text[j], 0)
            if depth == 0:
                break
        line = text[:start].count("\n") + 1
        return doc, text[start : j + 1], path.relative_to(ROOT), line
    raise SystemExit(f"example {name} not found")


def render():
    out = []
    for group, items in GROUPS:
        out.append(f"### {group}\n")
        for shot, title, fn in items:
            doc, code, path, line = find_example(fn)
            out.append(f"#### {title}\n")
            if doc:
                out.append(f"{doc}\n")
            out.append(
                f'<img src="screenshots/{shot}-light.webp" width="250" alt="{title}, light"> '
                f'<img src="screenshots/{shot}-dark.webp" width="250" alt="{title}, dark">\n'
            )
            out.append(
                f"<details><summary><code>{fn}()</code> in <a href=\"{path}#L{line}\">{path.name}</a></summary>\n\n"
                f"```kotlin\n{code}\n```\n\n</details>\n"
            )
    return "\n".join(out)


readme = README.read_text()
before, rest = readme.split(START, 1)
_, after = rest.split(END, 1)
README.write_text(f"{before}{START}\n\n{render()}\n{END}{after}")
