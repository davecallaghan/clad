#!/usr/bin/env python3
"""Mutation harness: proves each gate bites, on the record rather than in a docstring.

Modelled on ../constraint-escape/scripts/mutation_check.py. Countermeasure 6: a behaviour
described in a docstring counts only if a mutation proves it. Each entry reintroduces one defect
and names the ONE test that must fail (stronger than "the suite goes red", which would score a
collection error as a catch). A survivor means adding a test, never editing the mutation.

    python cicd/llm/mutation_check.py           # expect 0 survivors
    python cicd/llm/mutation_check.py --list

Honest limit, as in the original: this is authored by the same fallible process, and a mutation
nobody thinks to write is a constraint nobody has verified. It moves the boundary and makes it
countable; it does not remove it.
"""

from __future__ import annotations

import argparse
import atexit
import json
import signal
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
T = "cicd/llm/tests/"
G = "cicd/llm/gates/"


@dataclass(frozen=True)
class Mutation:
    gate: str
    name: str
    path: str
    old: str
    new: str
    expect_fail: str


EARLY_SATISFIED = "    return GateResult(GATE_ID, Verdict.SATISFIED, KIND)\n"

MUTATIONS: tuple[Mutation, ...] = (
    # ---- invented_limits ----------------------------------------------------------------------
    Mutation("invented_limits", "return SATISFIED early", G + "invented_limits.py",
             "    allowed, findings = load_allowlist(scope.root, cfg)\n",
             EARLY_SATISFIED + "    allowed, findings = load_allowlist(scope.root, cfg)\n",
             T + "test_invented_limits.py::test_each_canary_is_violated_with_its_rule[limit_key_py-limit_key]"),
    Mutation("invented_limits", "drop the constant-slice pattern", G + "invented_limits.py",
             "            if bound is not None:\n                self.out.add(node.lineno, \"constant_slice\"",
             "            if bound is None:\n                self.out.add(node.lineno, \"constant_slice\"",
             T + "test_invented_limits.py::test_negative_index_slice_is_flagged"),
    Mutation("invented_limits", "stop reading manifest timeouts (the 8-PR survivor)", G + "invented_limits.py",
             "    if is_manifest and name_matches(key, cfg[\"manifest_timeout_patterns\"]):",
             "    if False and is_manifest and name_matches(key, cfg[\"manifest_timeout_patterns\"]):",
             T + "test_invented_limits.py::test_each_canary_is_violated_with_its_rule[manifest_only_wrapper_timeout-manifest_timeout]"),
    Mutation("invented_limits", "accept an empty scope", G + "invented_limits.py",
             "    if len(scope) == 0:\n        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)\n    allowed",
             "    allowed",
             T + "test_invented_limits.py::test_empty_scope_is_inexpressible"),
    Mutation("invented_limits", "accept an allowlist entry without a source", G + "invented_limits.py",
             "        missing = [k for k in ALLOWLIST_FIELDS if e.get(k) in (None, \"\")]",
             "        missing = []",
             T + "test_invented_limits.py::test_allowlist_entry_missing_source_is_violated"),
    Mutation("invented_limits", "never tag escalation", G + "invented_limits.py",
             "    if prev is not None and v is not None and v > prev:",
             "    if False:",
             T + "test_invented_limits.py::test_raising_a_flagged_literal_is_tagged_escalation"),
    # ---- governed_write (library and gate) ----------------------------------------------------
    Mutation("governed_write", "skip a missing governed field instead of erroring", "cicd/llm/governed.py",
             "        if name not in row:\n            raise MissingGovernedField(",
             "        if name not in row:\n            continue\n            raise MissingGovernedField(",
             T + "test_governed.py::test_field_name_mismatch_fails_loudly"),
    Mutation("governed_write", "give the contract a default", "cicd/llm/governed.py",
             "def write_gate(rows, contract, target):",
             "def write_gate(rows, target, contract=None):",
             T + "test_governed.py::test_contract_is_a_required_positional_parameter_without_default"),
    Mutation("governed_write", "accept partial canary detection", "cicd/llm/governed.py",
             "    if missed:\n        detail",
             "    if missed and not caught:\n        detail",
             T + "test_governed.py::test_canary_fails_when_detection_is_partial"),
    Mutation("governed_write", "drop quarantined rows silently", "cicd/llm/governed.py",
             "        (outcome.quarantined if _out_of_domain(spec, row) else outcome.accepted).append(row)",
             "        _out_of_domain(spec, row) or outcome.accepted.append(row)",
             T + "test_governed.py::test_out_of_domain_value_is_quarantined_and_returned"),
    Mutation("governed_write", "accept an empty domain", "cicd/llm/governed.py",
             "    empty = [f for f, s in fields.items() if not s.get(\"domain\")]",
             "    empty = []",
             T + "test_governed.py::test_empty_domain_refuses_to_run"),
    Mutation("governed_write", "gate return SATISFIED early", G + "governed_write.py",
             "    findings, gated, unresolved, defs = [], 0, 0, 0\n",
             EARLY_SATISFIED + "    findings, gated, unresolved, defs = [], 0, 0, 0\n",
             T + "test_governed_write.py::test_each_canary_is_violated_with_its_rule[ungated_write-ungated_write]"),
    Mutation("governed_write", "any write_gate call gates any target", G + "governed_write.py",
             "            elif t is not None and t in gated_targets:",
             "            elif t is not None and gated_targets:",
             T + "test_governed_write.py::test_write_gated_for_a_different_target_is_still_ungated"),
    Mutation("governed_write", "ignore a defaulted contract parameter", G + "governed_write.py",
             "            if names.index(\"contract\") >= first_default:",
             "            if False:",
             T + "test_governed_write.py::test_each_canary_is_violated_with_its_rule[contract_default_none-contract_has_default]"),
    Mutation("governed_write", "silence the quarantine alarm", G + "governed_write.py",
             "        if n > 0 and not declared:",
             "        if n > 0 and declared:",
             T + "test_governed_write.py::test_expected_quarantine_without_a_source_does_not_count"),
    # ---- provenance ---------------------------------------------------------------------------
    Mutation("provenance", "two origins: promoted treated as a violation", G + "provenance.py",
             "ORIGINS = (\"model_emitted\", \"code_supplied\", \"promoted\")",
             "ORIGINS = (\"model_emitted\", \"code_supplied\")",
             T + "test_provenance.py::test_promoted_is_never_flagged"),
    Mutation("provenance", "skip a missing origin", G + "provenance.py",
             "        elif f\"{name}__origin\" not in row:",
             "        elif False:",
             T + "test_provenance.py::test_each_canary_is_violated_with_its_rule[missing_origin-missing_origin]"),
    Mutation("provenance", "accept code_supplied everywhere", G + "provenance.py",
             "        elif origin == \"code_supplied\" and \"code_supplied\" not in fspec.get(\"origins\", []) and row[name] != marker:",
             "        elif False:",
             T + "test_provenance.py::test_a_code_supplied_grade_is_violated"),
    Mutation("provenance", "accept an empty enumeration of outputs", G + "provenance.py",
             "    if rows == 0 and not findings:\n        return core.empty_scope(",
             "    if False:\n        return core.empty_scope(",
             T + "test_provenance.py::test_no_outputs_is_inexpressible"),
    # ---- behavioural_harness ------------------------------------------------------------------
    Mutation("behavioural_harness", "R1 never compares stored to emitted", G + "behavioural_harness.py",
             "            if any(v != value for v in stored):",
             "            if False:",
             T + "test_behavioural_harness.py::test_each_canary_is_violated_with_its_rule[assigned_constant-r1_not_dependent]"),
    Mutation("behavioural_harness", "R2 accepts a default grade", G + "behavioural_harness.py",
             "        if any(v != marker for v in stored):",
             "        if False:",
             T + "test_behavioural_harness.py::test_each_canary_is_violated_with_its_rule[default_grade-r2_default_grade]"),
    Mutation("behavioural_harness", "zero assertions pass", G + "behavioural_harness.py",
             "        if len(self.spec[\"fields\"][name][\"domain\"]) <= 1 or c[\"R1\"] == 0 or c[\"R2\"] == 0:",
             "        if False:",
             T + "test_behavioural_harness.py::test_each_canary_is_violated_with_its_rule[vacuous-vacuous]"),
    Mutation("behavioural_harness", "R4 never compares repeats", G + "behavioural_harness.py",
             "        if any(o != outputs[0] for o in outputs):",
             "        if False:",
             T + "test_behavioural_harness.py::test_r4_catches_nondeterministic_output"),
    # ---- review_findings ----------------------------------------------------------------------
    Mutation("review_findings", "skip citation validation", G + "review_findings.py",
             "        ok = [f for f in rv.get(\"findings\", []) if citation_valid(scope.root, rv[\"commit\"], f, registry)]",
             "        ok = list(rv.get(\"findings\", []))",
             T + "test_review_findings.py::test_hallucinated_citations_are_dropped_and_counted[bad2]"),
    Mutation("review_findings", "report uncorroborated findings", G + "review_findings.py",
             "                if fid in confirmed]",
             "                ]",
             T + "test_review_findings.py::test_uncorroborated_finding_is_not_reported"),
    Mutation("review_findings", "a reviewer may corroborate itself", G + "review_findings.py",
             "                 and valid[v[\"finding_id\"]][0] != rv[\"reviewer\"]}",
             "                 }",
             T + "test_review_findings.py::test_a_reviewer_confirming_its_own_finding_is_not_corroboration"),
    Mutation("review_findings", "the ladder never escalates", G + "review_findings.py",
             "        if counts[rule] >= ESCALATE_ON_REPEAT:",
             "        if False:",
             T + "test_review_findings.py::test_third_repeat_of_a_rule_escalates_to_a_human"),
    Mutation("review_findings", "ignore claimed independence", G + "review_findings.py",
             "    if cfg.get(\"independent\") is True and overlap:",
             "    if False:",
             T + "test_review_findings.py::test_claimed_independence_with_a_same_family_reviewer_is_violated"),
    # ---- safeguard_removal --------------------------------------------------------------------
    Mutation("safeguard_removal", "return SATISFIED early", G + "safeguard_removal.py",
             "    guards, marked = discover_guards(scope.root, [base, head], cfg)\n",
             EARLY_SATISFIED + "    guards, marked = discover_guards(scope.root, [base, head], cfg)\n",
             T + "test_safeguard_removal.py::test_each_canary_is_violated_with_its_rule[deleted_canary_call-deleted_safeguard_call]"),
    Mutation("safeguard_removal", "stop detecting broadened handlers", G + "safeguard_removal.py",
             "        if _broad(node):\n            self.add(\"broad_handler\"",
             "        if False:\n            self.add(\"broad_handler\"",
             T + "test_safeguard_removal.py::test_bare_except_counts_as_broadened"),
    Mutation("safeguard_removal", "stop detecting a try around a safeguard", G + "safeguard_removal.py",
             "            if self.in_try:",
             "            if False:",
             T + "test_safeguard_removal.py::test_each_canary_is_violated_with_its_rule[wrapped_safeguard-wrapped_safeguard]"),
    Mutation("safeguard_removal", "allow write_gate's result to be discarded", G + "safeguard_removal.py",
             "        if to_underscore and isinstance(node.value, ast.Call)",
             "        if False and isinstance(node.value, ast.Call)",
             T + "test_safeguard_removal.py::test_each_canary_is_violated_with_its_rule[discarded_quarantine-write_gate_result_discarded]"),
    Mutation("safeguard_removal", "accept a scope with no safeguards", G + "safeguard_removal.py",
             "    if not guards and not marked:",
             "    if False:",
             T + "test_safeguard_removal.py::test_no_safeguards_in_scope_is_violated"),
    Mutation("safeguard_removal", "ignore '# safeguard' markers", G + "safeguard_removal.py",
             "        self.marked = {i for i, line in enumerate(self.lines, start=1) if MARKER in line}",
             "        self.marked = set()",
             T + "test_safeguard_removal.py::test_safeguard_marker_comment_is_honoured"),
    # ---- completion_evidence ------------------------------------------------------------------
    Mutation("completion_evidence", "not_verified becomes optional", G + "completion_evidence.py",
             "    if not isinstance(d.get(\"not_verified\"), list):",
             "    if \"not_verified\" in d and not isinstance(d.get(\"not_verified\"), list):",
             T + "test_completion_evidence.py::test_each_canary_is_violated_with_its_rule[missing_not_verified-missing_not_verified]"),
    Mutation("completion_evidence", "complete despite an unsatisfied check", G + "completion_evidence.py",
             "        if complete and c.get(\"required\", True) and verdict != Verdict.SATISFIED.value:",
             "        if False:",
             T + "test_completion_evidence.py::test_each_canary_is_violated_with_its_rule[complete_with_not_run_check-complete_with_unsatisfied_check]"),
    Mutation("completion_evidence", "evidence need not exist", G + "completion_evidence.py",
             "        if not self.exists(c.get(\"evidence_path\")):",
             "        if False:",
             T + "test_completion_evidence.py::test_each_canary_is_violated_with_its_rule[missing_evidence_path-missing_evidence]"),
    Mutation("completion_evidence", "deployment needs no read-back", G + "completion_evidence.py",
             "        if c.get(\"claims_deployment\") and not self.exists(c.get(\"readback_evidence\")):",
             "        if False:",
             T + "test_completion_evidence.py::test_each_canary_is_violated_with_its_rule[deploy_without_readback-deployment_without_readback]"),
    Mutation("completion_evidence", "digests are not compared", G + "completion_evidence.py",
             "            if manifest.get(path) != digest:",
             "            if False:",
             T + "test_completion_evidence.py::test_each_canary_is_violated_with_its_rule[digest_mismatch-digest_mismatch]"),
    # ---- ruff_profile -------------------------------------------------------------------------
    Mutation("ruff_profile", "let noqa hide findings", G + "ruff_profile.py",
             "\"--isolated\", \"--ignore-noqa\", ",
             "\"--isolated\", ",
             T + "test_ruff_profile.py::test_a_noqa_naming_the_exact_code_does_not_hide_it"),
    Mutation("ruff_profile", "ratchet may go up", G + "ruff_profile.py",
             "    if previous is not None and current > previous:",
             "    if False:",
             T + "test_ruff_profile.py::test_raising_the_complexity_ratchet_is_violated"),
    Mutation("ruff_profile", "drop the typed-exception setting", G + "ruff_profile.py",
             "    for setting in cfg.get(\"ruff_settings\", []):",
             "    for setting in []:",
             T + "test_ruff_profile.py::test_each_canary_is_violated_with_its_code[try_except_pass-S110]"),
    Mutation("ruff_profile", "missing ruff passes", G + "ruff_profile.py",
             "        return core.not_run(GATE_ID, KIND, BLIND_SPOTS, \"ruff is not installed in this environment\")",
             "        return GateResult(GATE_ID, Verdict.SATISFIED, KIND)",
             T + "test_ruff_profile.py::test_missing_ruff_is_not_run_never_satisfied"),
    # ---- liveness -----------------------------------------------------------------------------
    Mutation("liveness", "a canary need not fire", G + "liveness.py",
             "            if verdict is Verdict.VIOLATED:",
             "            if True:",
             T + "test_liveness.py::test_a_gate_that_always_returns_satisfied_fails_liveness"),
    Mutation("liveness", "a gate without a canary is accepted", G + "liveness.py",
             "        if not canaries:\n            _flag(",
             "        if False:\n            _flag(",
             T + "test_liveness.py::test_a_deleted_canary_fails_liveness"),
    Mutation("liveness", "empty discovery is clean", G + "liveness.py",
             "    if not gates:\n        return GateResult(\"liveness\", Verdict.INEXPRESSIBLE",
             "    if False:\n        return GateResult(\"liveness\", Verdict.INEXPRESSIBLE",
             T + "test_liveness.py::test_no_gates_discovered_is_not_a_clean_result"),
    Mutation("liveness", "discover gates from a hand-written list", G + "liveness.py",
             "        if hasattr(module, \"GATE_ID\"):",
             "        if info.name in (\"invented_limits\", \"governed_write\", \"provenance\"):",
             T + "test_liveness.py::test_discovery_is_derived_from_the_package_not_a_list"),
    # ---- shared contract ----------------------------------------------------------------------
    Mutation("core", "a verdict coerces to bool", "cicd/lib/verdict.py",
             "    def __bool__(self) -> bool:\n        raise TypeError(",
             "    def __bool__(self) -> bool:\n        return True\n        raise TypeError(",
             T + "test_core.py::test_bool_of_a_verdict_raises[Verdict.NOT_RUN]"),
    Mutation("core", "an empty enumeration is SATISFIED", "cicd/llm/core.py",
             "    return GateResult(gate, Verdict.INEXPRESSIBLE, kind, blind_spots=blind_spots,\n                      notes=[EMPTY_ENUMERATION])",
             "    return GateResult(gate, Verdict.SATISFIED, kind, blind_spots=blind_spots,\n                      notes=[EMPTY_ENUMERATION])",
             T + "test_invented_limits.py::test_empty_scope_is_inexpressible"),
    Mutation("core", "'**/' no longer matches zero directories", "cicd/llm/core.py",
             "            out.append(\"(?:[^/]+/)*\")",
             "            out.append(\"[^/]+/\")",
             T + "test_core.py::test_glob_semantics[resources/wrapper.yml-**/resources/**/*.yml-True]"),
)


def run_named(test: str) -> bool:
    """True if the named test FAILS, which is what a caught mutation looks like."""
    r = subprocess.run([sys.executable, "-m", "pytest", "-q", "--no-header", "-p", "no:cacheprovider", test],
                       cwd=ROOT, capture_output=True, text=True)
    return r.returncode != 0


def collectable(test: str) -> bool:
    r = subprocess.run([sys.executable, "-m", "pytest", "-q", "--collect-only", "-p", "no:cacheprovider", test],
                       cwd=ROOT, capture_output=True, text=True)
    return r.returncode == 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--list", action="store_true", help="print the mutation set and exit")
    args = ap.parse_args()
    if args.list:
        for i, m in enumerate(MUTATIONS, 1):
            print(f"{i:>2}. [{m.gate}] {m.name}\n    {m.path} -> {m.expect_fail}")
        return 0

    # Refuse to run over a tree a previous (killed) run left mutated; restore on SIGINT/SIGTERM.
    leftovers = [f"{m.path}: {m.name}" for m in MUTATIONS
                 if m.new in (ROOT / m.path).read_text() and m.old not in (ROOT / m.path).read_text()]
    if leftovers:
        print("REFUSING TO RUN: a previous run left mutations in the working tree:")
        print("\n".join(f"  {x}" for x in leftovers))
        print("Restore them (git checkout -- <file>) before running this harness.")
        return 2
    in_flight: dict[Path, str] = {}

    def _restore_all(signum=None, frame=None):
        for pth, orig in list(in_flight.items()):
            pth.write_text(orig)
        in_flight.clear()
        if signum is not None:
            print(f"\ninterrupted (signal {signum}); working tree restored")
            sys.exit(130)

    atexit.register(_restore_all)
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, _restore_all)

    results, survivors = [], []
    for i, m in enumerate(MUTATIONS, 1):
        path = ROOT / m.path
        original = path.read_text()
        if m.old not in original or not collectable(m.expect_fail):
            why = "pattern absent: the code moved" if m.old not in original else "named test does not exist"
            print(f"{i:>2}. NOT APPLICABLE  [{m.gate}] {m.name}: {why}; update this mutation")
            survivors.append(m.name)
            results.append({"gate": m.gate, "name": m.name, "status": "not-applicable"})
        else:
            in_flight[path] = original
            path.write_text(original.replace(m.old, m.new, 1))
            try:
                caught = run_named(m.expect_fail)
            finally:
                path.write_text(original)
                in_flight.pop(path, None)
            print(f"{i:>2}. {'caught  ' if caught else 'SURVIVED'}  [{m.gate}] {m.name}")
            if not caught:
                survivors.append(m.name)
            results.append({"gate": m.gate, "name": m.name, "expect_fail": m.expect_fail,
                            "status": "caught" if caught else "survived"})

    per_gate: dict[str, list[int]] = {}
    for r in results:
        caught_total = per_gate.setdefault(r["gate"], [0, 0])
        caught_total[0] += r["status"] == "caught"
        caught_total[1] += 1
    print("\nper gate (caught/total): " + ", ".join(f"{g} {c}/{t}" for g, (c, t) in per_gate.items()))
    print(f"{len(MUTATIONS) - len(survivors)}/{len(MUTATIONS)} caught")
    out = ROOT / "cicd" / "llm" / "ledger" / "mutation_check.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"total": len(MUTATIONS), "survivors": survivors, "per_gate": per_gate,
                               "results": results}, indent=1) + "\n")
    if survivors:
        print("SURVIVORS: these constraints are not verified:")
        print("\n".join(f"   - {s}" for s in survivors))
        return 1
    print("every mutation caught by its named test")
    return 0


if __name__ == "__main__":
    sys.exit(main())
