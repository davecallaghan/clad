"""Install the guidance template into a client repository (guidance 3.6).

    python -m cicd.llm.templates.instantiate --target <repo> --project-name NAME --project-description TEXT

Writes `<repo>/CLAUDE.md` from CLAUDE.md.template, copies the skills to `<repo>/.claude/skills/`,
and merges the hooks into `<repo>/.claude/settings.json` (existing keys are kept; the merged result
is printed as a diff to review). It refuses to overwrite an existing CLAUDE.md, and refuses to
write anything that still contains a `{{PLACEHOLDER}}`.
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PLACEHOLDER = re.compile(r"\{\{[A-Z_]+\}\}")


class TemplateError(Exception):
    pass


def render(text: str, values: dict[str, str]) -> str:
    for key, value in values.items():
        text = text.replace("{{" + key + "}}", value)
    left = sorted(set(PLACEHOLDER.findall(text)))
    if left:
        raise TemplateError(f"unfilled placeholders: {left}")
    return text


def merge_settings(existing: dict, hooks: dict) -> dict:
    merged = dict(existing)
    merged_hooks = dict(existing.get("hooks", {}))
    for event, groups in hooks["hooks"].items():
        merged_hooks[event] = [*merged_hooks.get(event, []), *[g for g in groups if g not in merged_hooks.get(event, [])]]
    merged["hooks"] = merged_hooks
    return merged


def instantiate(target: Path, values: dict[str, str]) -> str:
    claude_md = target / "CLAUDE.md"
    if claude_md.exists():
        raise TemplateError(f"{claude_md} exists; merge by hand and review the diff (it is never overwritten)")
    rendered = render((HERE / "CLAUDE.md.template").read_text(encoding="utf-8"), values)
    settings_path = target / ".claude" / "settings.json"
    before = settings_path.read_text(encoding="utf-8") if settings_path.is_file() else "{}\n"
    after = json.dumps(merge_settings(json.loads(before), json.loads((HERE / "settings.hooks.json").read_text())),
                       indent=2) + "\n"
    claude_md.write_text(rendered, encoding="utf-8")
    shutil.copytree(HERE / "skills", target / ".claude" / "skills", dirs_exist_ok=True)
    settings_path.parent.mkdir(parents=True, exist_ok=True)
    settings_path.write_text(after, encoding="utf-8")
    return "".join(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                        ".claude/settings.json (before)", ".claude/settings.json (after)"))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--target", type=Path, required=True)
    ap.add_argument("--project-name", required=True)
    ap.add_argument("--project-description", required=True)
    a = ap.parse_args()
    try:
        diff = instantiate(a.target, {"PROJECT_NAME": a.project_name, "PROJECT_DESCRIPTION": a.project_description})
    except TemplateError as exc:
        print(f"REFUSED: {exc}", file=sys.stderr)
        return 1
    print(diff or ".claude/settings.json unchanged")
    return 0


if __name__ == "__main__":
    sys.exit(main())
