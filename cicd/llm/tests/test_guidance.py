"""Guidance tests (guidance 3.7): the guidance must not drift from the gates.

Everything is derived: gates from cicd/llm/gates/, skills from .claude/skills/, examples from each
skill's examples/, non-negotiables from CLAUDE.md. Examples run through the real gates.
"""

from __future__ import annotations

import json
import re
import tempfile
import tomllib
from pathlib import Path

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import examples
from cicd.llm.gates import liveness
from cicd.llm.templates import instantiate as tpl
from cicd.llm.tests import test_hooks as hooks
from cicd.llm.tests.helpers import KIT, write_files

ROOT = KIT.parents[1]
SKILLS = ROOT / ".claude" / "skills"
TEMPLATE_SKILLS = KIT / "templates" / "skills"
CLAUDE_MD = ROOT / "CLAUDE.md"
GATE_IDS = set(liveness.discover_gates())
EXAMPLES = sorted(SKILLS.glob("*/examples/*"))
NON_NEGOTIABLE = re.compile(r"^- \*\*(?P<id>[A-Z-]+)\.\*\* (?P<body>.+)$")
CHECKED_BY = re.compile(r"Checked by: (?P<ids>[^\n]+)")


def skill_dirs() -> list[Path]:
    return sorted(p for p in SKILLS.iterdir() if p.is_dir())


def frontmatter(skill: Path) -> dict:
    text = (skill / "SKILL.md").read_text()
    assert text.startswith("---\n"), f"{skill.name}: SKILL.md must start with YAML frontmatter"
    block = text.split("---\n", 2)[1]
    return dict(line.split(": ", 1) for line in block.strip().splitlines())


def claimed_gates(skill: Path) -> set[str]:
    m = CHECKED_BY.search((skill / "SKILL.md").read_text())
    assert m, f"{skill.name}: no 'Checked by:' line"
    clause = re.split(r"\(|\.(?:\s|$)", m.group("ids"))[0]  # the IDs end at "(" or a sentence stop
    return set(re.findall(r"[a-z][a-z_]*", clause))


def non_negotiables() -> list[re.Match]:
    section = CLAUDE_MD.read_text().split("## Non-negotiables", 1)[1].split("\n## ", 1)[0]
    return [m for line in section.splitlines() if (m := NON_NEGOTIABLE.match(line))]


# ---- 1. examples behave as named, through the real gates ---------------------------------------

def test_examples_are_derived_and_non_empty():
    assert EXAMPLES and GATE_IDS


@pytest.mark.parametrize("example", EXAMPLES, ids=lambda p: f"{p.parents[1].name}/{p.name}")
def test_examples_behave_as_named(example):
    gate, kind, _ = examples.parse_name(example)
    result = examples.run_example(example)
    assert result.verdict is examples.EXPECTED[kind], (
        f"{example.name} should be {examples.EXPECTED[kind].value} under {gate}, got {result.verdict.value}: "
        f"{[f.to_dict() for f in result.findings]} {result.notes}")


# ---- 2. coverage both ways ----------------------------------------------------------------------

def test_every_gate_is_covered_by_a_skill_and_a_non_negotiable():
    by_skills = set().union(*(claimed_gates(s) for s in skill_dirs()))
    by_rules = {set(re.findall(r"Checked by: ([a-z_]+)", m.group("body"))).pop() for m in non_negotiables()
                if "Checked by: " in m.group("body")}
    assert GATE_IDS - by_skills == set(), "gates with no skill"
    assert GATE_IDS - by_rules == set(), "gates with no non-negotiable"


def test_every_gate_id_named_in_the_guidance_exists():
    named = set().union(*(claimed_gates(s) for s in skill_dirs()))
    named |= {examples.parse_name(p)[0] for p in EXAMPLES}
    named |= set(re.findall(r"Checked by: ([a-z_]+)", CLAUDE_MD.read_text()))
    assert named - GATE_IDS == set()


# ---- 3. non-negotiable form ---------------------------------------------------------------------

def test_non_negotiables_are_derived_and_one_per_gate():
    ids = [m.group("id") for m in non_negotiables()]
    assert len(ids) == len(set(ids)) == len(GATE_IDS)


@pytest.mark.parametrize("rule", [m.group("id") for m in non_negotiables()])
def test_each_non_negotiable_states_applicability_and_its_check(rule):
    body = next(m.group("body") for m in non_negotiables() if m.group("id") == rule)
    assert "Applies when:" in body and "Does not apply when:" in body
    gated = re.search(r"Checked by: ([a-z_]+)$", body)
    assert gated or "(advisory: no gate)" in body
    if gated:
        assert gated.group(1) in GATE_IDS


def test_non_negotiable_text_is_the_rule_registry_text():
    """Reviewers quote rule text from cicd/llm/rules.toml; CLAUDE.md must say the same thing."""
    registry = tomllib.loads((KIT / "rules.toml").read_text())["rules"]
    for m in non_negotiables():
        rule = registry[f"LLM-{m.group('id').removeprefix('LLM-')}"]
        assert m.group("body").startswith(rule["text"]), m.group("id")
        assert m.group("body").endswith(f"Checked by: {rule['gate']}")


# ---- 4. skills ----------------------------------------------------------------------------------

@pytest.mark.parametrize("skill", skill_dirs(), ids=lambda p: p.name)
def test_skill_frontmatter_has_name_and_description(skill):
    fm = frontmatter(skill)
    assert fm.get("name") == skill.name
    assert fm.get("description")


@pytest.mark.parametrize("skill", skill_dirs(), ids=lambda p: p.name)
def test_each_claimed_gate_has_a_good_and_a_bad_example(skill):
    have = {(gate, kind) for gate, kind, _ in map(examples.parse_name, (skill / "examples").iterdir())}
    for gate in claimed_gates(skill):
        assert {(gate, "good"), (gate, "bad")} <= have, f"{skill.name}: {gate}"


@pytest.mark.parametrize("skill", skill_dirs(), ids=lambda p: p.name)
def test_every_linked_example_exists(skill):
    links = re.findall(r"\]\((examples/[^)]+)\)", (skill / "SKILL.md").read_text())
    missing = [link for link in links if not (skill / link).exists()]
    assert missing == []


def test_gate_preflight_examples_are_copies_of_the_owning_skills_examples():
    originals = {p.name: p.read_bytes() for p in EXAMPLES if p.parents[1].name != "gate-preflight"}
    for p in (SKILLS / "gate-preflight" / "examples").iterdir():
        assert originals.get(p.name) == p.read_bytes(), p.name


# ---- 5. hook canaries (guidance 3.5) ------------------------------------------------------------

def test_hook_canary_edit_adding_an_invented_limit_is_reported(make_repo):
    root = hooks.project(make_repo)
    write_files(root, {"a.py": "max_rows = 500\n"})
    r = hooks.run_hook("PostToolUse", hooks.edit_payload(root / "a.py"))
    assert r.returncode == hooks.BLOCK and "invented_limits" in r.stderr


def test_hook_canary_stop_without_completion_after_a_governed_change_blocks(make_repo):
    root = hooks.project(make_repo)
    write_files(root, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    r = hooks.run_hook("Stop", hooks.stop_payload(root))
    assert hooks.blocked(r)


# ---- template (guidance 3.6) --------------------------------------------------------------------

def test_template_skills_do_not_drift_from_the_repository_skills():
    repo = {p.relative_to(SKILLS): p.read_bytes() for p in SKILLS.rglob("*") if p.is_file()}
    tmpl = {p.relative_to(TEMPLATE_SKILLS): p.read_bytes() for p in TEMPLATE_SKILLS.rglob("*") if p.is_file()}
    assert repo == tmpl


def test_template_non_negotiables_match_the_repository():
    assert CLAUDE_MD.read_text().split("## Non-negotiables", 1)[1] == \
        (KIT / "templates" / "CLAUDE.md.template").read_text().split("## Non-negotiables", 1)[1]


def test_template_instantiates_without_placeholders_into_a_fresh_repo():
    with tempfile.TemporaryDirectory() as d:
        target = Path(d)
        tpl.instantiate(target, {"PROJECT_NAME": "bundle", "PROJECT_DESCRIPTION": "A bundle."})
        assert not tpl.PLACEHOLDER.search((target / "CLAUDE.md").read_text())
        assert json.loads((target / ".claude" / "settings.json").read_text())["hooks"]
        assert sorted(p.name for p in (target / ".claude" / "skills").iterdir()) == [p.name for p in skill_dirs()]


def test_template_refuses_unfilled_placeholders():
    with tempfile.TemporaryDirectory() as d, pytest.raises(tpl.TemplateError, match="unfilled"):
        tpl.instantiate(Path(d), {"PROJECT_NAME": "bundle"})


def test_verdict_meanings_are_stated():
    text = " ".join(CLAUDE_MD.read_text().split())  # wrapped lines read as one sentence
    for v in (Verdict.NOT_RUN, Verdict.INEXPRESSIBLE):
        assert v.value in text
    assert "**not passes**" in text
