"""Gate 7: completion_evidence (kind = deciding).

Exists to catch: a completion claim without evidence. Evidence (constraint-escape, "E2, the
live-agent arm"): 6 of 12 agent completion claims were made on systems whose deployed artifact
was wrong, because nothing the agent was shown reported the deployed digest. "Practitioner
implications" item 7: a completion object makes the absence of evidence explicit and
mechanically rejectable, which a prose instruction cannot.

Schema (`completion.json`):

    {"status": "complete" | "<anything else>", "commit": "<sha>", "tree_hash": "<sha>",
     "artifact_digests": {"<path>": "sha256:<hex>"},
     "checks": [{"id": "<check>", "verdict": "SATISFIED|VIOLATED|INEXPRESSIBLE|NOT_RUN",
                 "evidence_path": "<path>", "required": true,
                 "claims_deployment": false, "readback_evidence": "<path>"}],
     "not_verified": ["<surface or sub-check not verified>"],
     "build_manifest": "<optional path to {\"digests\": {path: digest}}>",
     ...free-text fields (recorded, never scored)}

VIOLATED when: `not_verified` is missing (empty is allowed); a schema field is missing; a verdict
is not one of the four; status is `complete` while a required check is not SATISFIED; an
evidence path does not exist; a digest does not match the build manifest (when one is supplied);
a check claims deployment without read-back evidence, or with read-back evidence that is the deploy
evidence itself or contains none of the claimed artifact digests; status is not exactly
"complete" or "incomplete".
"""

from __future__ import annotations

import json
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope

GATE_ID = "completion_evidence"
KIND = "deciding"
SCHEMA = ("status", "commit", "tree_hash", "artifact_digests", "checks")
STATUSES = ("complete", "incomplete")  # v2: a synonym like "done" claimed completion while evading the check
STRUCTURED = set(SCHEMA) | {"not_verified", "build_manifest"}
BLIND_SPOTS = [
    "whether an evidence file says what its check claims (existence is checked, content is not)",
    "checks the claim does not list",
    "free-text fields (recorded, never scored)",
]


def completion_scope(root: Path, config: dict, opts: dict) -> Scope:
    rel = opts.get("completion") or core.gate_section(config, GATE_ID)["completion"]
    return Scope(root=root, files=[rel] if (root / rel).is_file() else [])


class _Check:
    def __init__(self, root: Path, rel: str, claimed: list[str]):
        self.root, self.rel, self.findings, self.claimed = root, rel, [], claimed

    def flag(self, rule: str, message: str) -> None:
        self.findings.append(Finding(self.rel, 1, core.line_hash(message), rule, message))

    def exists(self, path) -> bool:
        return isinstance(path, str) and bool(path) and (self.root / path).exists()

    def check(self, c: dict, complete: bool) -> None:
        cid, verdict = c.get("id", "<no id>"), c.get("verdict")
        if verdict not in {v.value for v in Verdict}:
            self.flag("invalid_verdict", f"check {cid!r} has verdict {verdict!r}, not one of the four")
        if complete and c.get("required", True) and verdict != Verdict.SATISFIED.value:
            self.flag("complete_with_unsatisfied_check", f"status is complete but required check {cid!r} is {verdict}")
        if not self.exists(c.get("evidence_path")):
            self.flag("missing_evidence", f"check {cid!r}: evidence_path {c.get('evidence_path')!r} does not exist")
        if c.get("claims_deployment"):
            self.readback(cid, c)

    def readback(self, cid: str, c: dict) -> None:
        rb = c.get("readback_evidence")
        if not self.exists(rb):
            self.flag("deployment_without_readback", f"check {cid!r} claims deployment without read-back evidence")
        elif rb == c.get("evidence_path"):
            self.flag("readback_is_deploy_evidence",
                      f"check {cid!r}: read-back evidence is the deploy evidence itself, not an independent read")
        elif not any(d in (self.root / rb).read_text(encoding="utf-8", errors="replace") for d in self.claimed):
            self.flag("readback_without_digest",
                      f"check {cid!r}: read-back evidence contains none of the claimed artifact digests")

    def digests(self, d: dict) -> str:
        if not d.get("build_manifest"):
            return Verdict.NOT_RUN.value
        manifest = json.loads((self.root / d["build_manifest"]).read_text(encoding="utf-8")).get("digests", {})
        for path, digest in sorted(d.get("artifact_digests", {}).items()):
            if manifest.get(path) != digest:
                self.flag("digest_mismatch", f"{path}: claimed {digest}, build manifest has {manifest.get(path)}")
        return "RUN"


def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    if len(scope) == 0:
        return core.not_run(GATE_ID, KIND, BLIND_SPOTS, "no completion file: nothing claims completion")
    rel = scope.files[0]
    d = json.loads((scope.root / rel).read_text(encoding="utf-8"))
    c = _Check(scope.root, rel, [str(v) for v in (d.get("artifact_digests") or {}).values()])
    if not isinstance(d.get("not_verified"), list):
        c.flag("missing_not_verified", "not_verified is required (an empty list is allowed, a missing key is not)")
    for key in (k for k in SCHEMA if k not in d):
        c.flag("schema", f"required field {key!r} is missing")
    if d.get("status") not in STATUSES:
        c.flag("invalid_status", f"status {d.get('status')!r} is not one of {STATUSES}")
    complete = d.get("status") == "complete"
    for check in d.get("checks", []):
        c.check(check, complete)
    digest_state = c.digests(d)
    return GateResult(GATE_ID, Verdict.VIOLATED if c.findings else Verdict.SATISFIED, KIND,
                      findings=c.findings, scope_size=len(scope), blind_spots=BLIND_SPOTS,
                      sub_checks={"schema": "RUN", "checks": "RUN", "digests": digest_state},
                      details={"status": d.get("status"), "not_verified": d.get("not_verified"),
                               "free_text": {k: v for k, v in d.items() if k not in STRUCTURED}})


def run_canary(canary_dir: Path) -> GateResult:
    config, opts = core.canary_setup(canary_dir)
    return run(config, completion_scope(canary_dir, config, opts), opts)


def _args(ap) -> None:
    ap.add_argument("--completion", help="completion JSON (default from config)")


def _scope(root: Path, config: dict, args) -> Scope:
    return completion_scope(root, config, vars(args))


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, run, __doc__, add_args=_args, scope_fn=_scope))
