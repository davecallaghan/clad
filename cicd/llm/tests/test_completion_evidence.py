"""Gate 7, completion_evidence (escape, "E2, the live-agent arm"; "Practitioner implications" 7)."""

from __future__ import annotations

import json

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import completion_evidence as g
from cicd.llm.tests.helpers import CANARIES


def doc(**over):
    d = {"status": "complete", "commit": "c" * 40, "tree_hash": "t" * 40,
         "artifact_digests": {"a.tar": "sha256:" + "a" * 64},
         "checks": [{"id": "tests", "verdict": "SATISFIED", "evidence_path": "ev.txt"}],
         "not_verified": []}
    d.update(over)
    return d


def run_doc(make_repo, kit_config, d, extra=None):
    root = make_repo({"completion.json": json.dumps(d), "ev.txt": "ok\n", **(extra or {})})
    cfg = core.deep_merge(kit_config, {"gates": {"completion_evidence": {"completion": "completion.json"}}})
    return g.run(cfg, g.completion_scope(root, cfg, {}), {})


def rules(r):
    return {f.rule for f in r.findings}


@pytest.mark.parametrize("canary,rule", [
    ("complete_with_not_run_check", "complete_with_unsatisfied_check"),
    ("missing_not_verified", "missing_not_verified"),
    ("missing_evidence_path", "missing_evidence"),
    ("digest_mismatch", "digest_mismatch"),
    ("deploy_without_readback", "deployment_without_readback"),
])
def test_each_canary_is_violated_with_its_rule(canary, rule):
    r = g.run_canary(CANARIES / "completion_evidence" / canary)
    assert r.verdict is Verdict.VIOLATED
    assert rule in rules(r), rules(r)


def test_valid_completion_is_satisfied(make_repo, kit_config):
    r = run_doc(make_repo, kit_config, doc())
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]


def test_empty_not_verified_is_allowed_but_missing_is_not(make_repo, kit_config):
    assert run_doc(make_repo, kit_config, doc(not_verified=[])).verdict is Verdict.SATISFIED
    d = doc()
    del d["not_verified"]
    assert "missing_not_verified" in rules(run_doc(make_repo, kit_config, d))


def test_incomplete_status_may_carry_unsatisfied_checks(make_repo, kit_config):
    checks = [{"id": "x", "verdict": "NOT_RUN", "evidence_path": "ev.txt"}]
    r = run_doc(make_repo, kit_config, doc(status="incomplete", checks=checks))
    assert r.verdict is Verdict.SATISFIED


def test_non_required_check_does_not_block_completion(make_repo, kit_config):
    checks = [{"id": "x", "verdict": "SATISFIED", "evidence_path": "ev.txt"},
              {"id": "y", "verdict": "NOT_RUN", "evidence_path": "ev.txt", "required": False}]
    assert run_doc(make_repo, kit_config, doc(checks=checks)).verdict is Verdict.SATISFIED


def test_unknown_verdict_value_is_violated(make_repo, kit_config):
    checks = [{"id": "x", "verdict": "PASSED", "evidence_path": "ev.txt"}]
    assert "invalid_verdict" in rules(run_doc(make_repo, kit_config, doc(checks=checks)))


def test_missing_schema_field_is_violated(make_repo, kit_config):
    d = doc()
    del d["tree_hash"]
    assert "schema" in rules(run_doc(make_repo, kit_config, d))


def test_matching_build_manifest_is_satisfied(make_repo, kit_config):
    manifest = json.dumps({"digests": {"a.tar": "sha256:" + "a" * 64}})
    r = run_doc(make_repo, kit_config, doc(build_manifest="m.json"), {"m.json": manifest})
    assert r.verdict is Verdict.SATISFIED
    assert r.sub_checks["digests"] == "RUN"


def test_digest_check_is_not_run_without_a_manifest(make_repo, kit_config):
    assert run_doc(make_repo, kit_config, doc()).sub_checks["digests"] == "NOT_RUN"


def test_deployment_claim_with_readback_evidence_is_satisfied(make_repo, kit_config):
    checks = [{"id": "deploy", "verdict": "SATISFIED", "evidence_path": "ev.txt",
               "claims_deployment": True, "readback_evidence": "rb.txt"}]
    readback = {"rb.txt": "workspace file a.tar: sha256:" + "a" * 64 + "\n"}
    assert run_doc(make_repo, kit_config, doc(checks=checks), readback).verdict is Verdict.SATISFIED


def test_free_text_is_recorded_and_never_scored(make_repo, kit_config):
    r = run_doc(make_repo, kit_config, doc(summary="All checks pass and it is fully done."))
    assert r.verdict is Verdict.SATISFIED
    assert r.details["free_text"] == {"summary": "All checks pass and it is fully done."}


def test_missing_completion_file_is_not_run(make_repo, kit_config):
    root = make_repo({"x.txt": "x\n"})
    cfg = core.deep_merge(kit_config, {"gates": {"completion_evidence": {"completion": "completion.json"}}})
    r = g.run(cfg, g.completion_scope(root, cfg, {}), {})
    assert r.verdict is Verdict.NOT_RUN


# ---- v2 ------------------------------------------------------------------------------------------

def test_status_must_be_complete_or_incomplete(make_repo, kit_config):
    assert "invalid_status" in rules(run_doc(make_repo, kit_config, doc(status="done")))
    assert run_doc(make_repo, kit_config, doc(status="incomplete")).verdict is Verdict.SATISFIED


def test_readback_must_contain_a_claimed_digest(make_repo, kit_config):
    checks = [{"id": "deploy", "verdict": "SATISFIED", "evidence_path": "ev.txt",
               "claims_deployment": True, "readback_evidence": "rb.txt"}]
    r = run_doc(make_repo, kit_config, doc(checks=checks), {"rb.txt": "Deployment complete!\n"})
    assert "readback_without_digest" in rules(r)


def test_readback_cannot_be_the_deploy_evidence_itself(make_repo, kit_config):
    checks = [{"id": "deploy", "verdict": "SATISFIED", "evidence_path": "ev.txt",
               "claims_deployment": True, "readback_evidence": "ev.txt"}]
    assert "readback_is_deploy_evidence" in rules(run_doc(make_repo, kit_config, doc(checks=checks)))
