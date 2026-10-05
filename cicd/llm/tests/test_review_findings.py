"""Gate 5, review_findings: the deterministic half of cross-architecture review
(constraint-reversion, "Cross-architecture review, reported as an observation")."""

from __future__ import annotations

import json

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import review_findings as g
from cicd.llm.tests.helpers import CANARIES, commit_all

THIRD_REPEAT = 3  # constraint-reversion, "Cross-architecture review": the third repeat escalates
REG = '[rules.R1]\ngate = "invented_limits"\ntext = "Do not introduce a numeric limit."\n'
SRC = "def load(rows):\n    max_rows = 500\n    return rows[:max_rows]\n"


def finding(fid="a1", **over):
    f = {"id": fid, "file": "src.py", "line": 2, "quote": "max_rows = 500", "rule_id": "R1",
         "rule_quote": "Do not introduce a numeric limit.", "message": "cap"}
    f.update(over)
    return f


def review(name, commit, findings=(), verdicts=(), round_=1):
    return json.dumps({"reviewer": name, "commit": commit, "pr": "7", "round": round_,
                       "findings": list(findings), "verdicts": list(verdicts)})


def setup(make_repo, a_findings, b_verdicts, b_findings=()):
    root = make_repo({"src.py": SRC, "rules.toml": REG})
    sha = commit_all(root, "reviewed")
    (root / "reviews").mkdir()
    (root / "reviews" / "a.json").write_text(review("a", sha, a_findings))
    (root / "reviews" / "b.json").write_text(review("b", sha, b_findings, b_verdicts))
    return root


def run_dir(root, kit_config, **opts):
    cfg = core.deep_merge(kit_config, {"gates": {"review_findings": {"rule_registry": "rules.toml"}}})
    o = {"findings_dir": "reviews", **opts}
    return g.run(cfg, g.review_scope(root, cfg, o), o)


def run_cfg(root, kit_config, extra, **opts):
    cfg = core.deep_merge(kit_config, {"gates": {"review_findings": {"rule_registry": "rules.toml", **extra}}})
    o = {"findings_dir": "reviews", **opts}
    return g.run(cfg, g.review_scope(root, cfg, o), o)


@pytest.mark.parametrize("canary,check", [
    ("corroborated", lambda r: r.details["reported"] == 1),
    ("escalation", lambda r: r.details["escalation"] == {"LLM-LIMITS": "ESCALATE_TO_HUMAN"}),
    ("not_independent", lambda r: "not_independent" in {f.rule for f in r.findings}),
])
def test_each_canary_is_violated(canary, check):
    r = g.run_canary(CANARIES / "review_findings" / canary)
    assert r.verdict is Verdict.VIOLATED
    assert check(r), r.details


def test_corroborated_valid_finding_is_reported(make_repo, kit_config):
    root = setup(make_repo, [finding()], [{"finding_id": "a1", "verdict": "confirm"}])
    r = run_dir(root, kit_config)
    assert r.verdict is Verdict.VIOLATED
    assert r.details["reported"] == 1


def test_uncorroborated_finding_is_not_reported(make_repo, kit_config):
    root = setup(make_repo, [finding()], [{"finding_id": "a1", "verdict": "reject"}])
    r = run_dir(root, kit_config)
    assert r.verdict is Verdict.SATISFIED
    assert r.details["uncorroborated"] == 1


@pytest.mark.parametrize("bad", [
    {"file": "nope.py"},
    {"line": 99},
    {"quote": "max_rows = 9999"},
    {"quote": ""},
    {"rule_id": "R404"},
    {"rule_quote": "A rule nobody wrote."},
])
def test_hallucinated_citations_are_dropped_and_counted(make_repo, kit_config, bad):
    root = setup(make_repo, [finding(**bad)], [{"finding_id": "a1", "verdict": "confirm"}])
    r = run_dir(root, kit_config)
    assert r.details["dropped"] == {"a": 1, "b": 0}
    assert r.details["reported"] == 0
    assert r.verdict is Verdict.SATISFIED


def test_citation_is_checked_at_the_reviewed_commit_not_the_worktree(make_repo, kit_config):
    root = setup(make_repo, [finding()], [{"finding_id": "a1", "verdict": "confirm"}])
    (root / "src.py").write_text("def load(rows):\n    return rows\n")
    commit_all(root, "later change")
    assert run_dir(root, kit_config).details["reported"] == 1


def test_third_repeat_of_a_rule_escalates_to_a_human(make_repo, kit_config, tmp_path):
    state = tmp_path / "ladder.json"
    root = setup(make_repo, [finding()], [{"finding_id": "a1", "verdict": "confirm"}])
    results = [run_dir(root, kit_config, state=str(state)) for _ in range(3)]
    assert [r.details["escalation"] for r in results] == [{}, {}, {"R1": "ESCALATE_TO_HUMAN"}]
    assert json.loads(state.read_text())["rule_counts"] == {"R1": 3}


def test_escalation_ladder_is_not_run_without_state(make_repo, kit_config):
    root = setup(make_repo, [finding()], [{"finding_id": "a1", "verdict": "confirm"}])
    assert run_dir(root, kit_config).sub_checks["escalation_ladder"] == "NOT_RUN"


def test_escalation_threshold_is_the_paper_constant():
    """reversion, "Cross-architecture review": human intervention on the third repeat."""
    assert g.ESCALATE_ON_REPEAT == THIRD_REPEAT


def test_independence_overlap_is_reported_without_a_violation_when_not_claimed(make_repo, kit_config):
    root = setup(make_repo, [], [])
    r = run_cfg(root, kit_config, {"author_family": "claude", "reviewers": {"a": "claude", "b": "qwen"}})
    assert r.details["family_overlap"] == ["a"]
    assert r.verdict is Verdict.SATISFIED


def test_claimed_independence_with_a_same_family_reviewer_is_violated(make_repo, kit_config):
    """reversion, "The gate is half same-family, and an earlier version of this paper said otherwise"."""
    root = setup(make_repo, [], [])
    r = run_cfg(root, kit_config, {"author_family": "claude", "independent": True,
                                   "reviewers": {"a": "claude", "b": "qwen"}})
    assert "not_independent" in {f.rule for f in r.findings}


def test_independence_is_not_run_without_families(make_repo, kit_config):
    root = setup(make_repo, [], [])
    assert run_dir(root, kit_config).sub_checks["independence"] == "NOT_RUN"


def test_no_review_files_is_inexpressible(make_repo, kit_config):
    root = make_repo({"rules.toml": REG})
    (root / "reviews").mkdir()
    assert run_dir(root, kit_config).verdict is Verdict.INEXPRESSIBLE


def test_no_findings_dir_is_not_run(make_repo, kit_config):
    root = make_repo({"rules.toml": REG})
    cfg = core.deep_merge(kit_config, {"gates": {"review_findings": {"rule_registry": "rules.toml"}}})
    r = g.run(cfg, g.review_scope(root, cfg, {}), {})
    assert r.verdict is Verdict.NOT_RUN


def test_a_reviewer_confirming_its_own_finding_is_not_corroboration(make_repo, kit_config):
    root = make_repo({"src.py": SRC, "rules.toml": REG})
    sha = commit_all(root, "reviewed")
    (root / "reviews").mkdir()
    (root / "reviews" / "a.json").write_text(
        review("a", sha, [finding()], [{"finding_id": "a1", "verdict": "confirm"}]))
    (root / "reviews" / "b.json").write_text(review("b", sha))
    r = run_dir(root, kit_config)
    assert r.details["reported"] == 0
    assert r.verdict is Verdict.SATISFIED


def test_quote_matching_is_whitespace_insensitive(make_repo, kit_config):
    """v2: a valid finding was dropped because the reviewer collapsed a run of spaces."""
    root = make_repo({"src.py": "def load(rows):\n    max_rows   =   500\n    return rows\n", "rules.toml": REG})
    sha = commit_all(root, "reviewed")
    (root / "reviews").mkdir()
    (root / "reviews" / "a.json").write_text(review("a", sha, [finding(quote="max_rows = 500")]))
    (root / "reviews" / "b.json").write_text(review("b", sha, verdicts=[{"finding_id": "a1", "verdict": "confirm"}]))
    assert run_dir(root, kit_config).details["reported"] == 1
