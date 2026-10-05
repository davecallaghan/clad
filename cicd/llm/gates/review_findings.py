"""Gate 5: review_findings (deterministic half of cross-architecture review; kind = proxy).

Exists to catch: hallucinated reviewer findings entering the record, uncorroborated findings
being acted on, a model that does not self-correct on a rule, and a review credited with an
independence it does not have. Evidence (constraint-reversion, "Cross-architecture review,
reported as an observation"): a programmatic stage removes findings whose cited file, line or
rule quotation does not exist; only findings the other reviewer corroborates are reported; an
escalation ladder declares human intervention on the third repeat of a rule within a PR; "The
gate is half same-family": one of the two reviewers shared the author's training family while the
control was described as independent. No model is called here.

Input: `--findings-dir DIR`, one JSON file per reviewer:

    {"reviewer": "<name>", "commit": "<reviewed commit sha>", "pr": "<id>", "round": <n>,
     "findings": [{"id": "<unique>", "file": "<repo path>", "line": <1-based>,
                   "quote": "<text on that line>", "rule_id": "<registry id>",
                   "rule_quote": "<text from the rule>", "message": "<free text>"}],
     "verdicts": [{"finding_id": "<other reviewer's finding id>", "verdict": "confirm" | "reject"}]}

`commit` may be "WORKTREE" for fixtures, meaning the files on disk. Two Databricks serving
endpoints can feed this by writing one such file each.
"""

from __future__ import annotations

import json
import subprocess
import tomllib
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope

GATE_ID = "review_findings"
KIND = "proxy"
# constraint-reversion, "Cross-architecture review": human intervention on the third repeat of
# the same rule within a pull request.
ESCALATE_ON_REPEAT = 3
BLIND_SPOTS = [
    "whether a validated, corroborated finding is correct",
    "reviewer capability (the paper's binding constraint); only citation validity is checked",
    "anything no reviewer cited",
]


def review_scope(root: Path, config: dict, opts: dict) -> Scope:
    d = opts.get("findings_dir")
    if not d:
        return Scope(root=root, files=[])
    base = root / d
    return Scope(root=root, files=sorted(str(p.relative_to(root)) for p in base.glob("*.json") if p.is_file()))


def _file_lines(root: Path, commit: str, path: str) -> list[str] | None:
    if commit == "WORKTREE":
        p = root / path
        return p.read_text(encoding="utf-8").splitlines() if p.is_file() else None
    r = subprocess.run(["git", "show", f"{commit}:{path}"], cwd=root, capture_output=True, text=True)
    return r.stdout.splitlines() if r.returncode == 0 else None


def citation_valid(root: Path, commit: str, f: dict, registry: dict) -> bool:
    lines = _file_lines(root, commit, str(f.get("file", "")))
    line, quote = f.get("line"), str(f.get("quote", "")).strip()
    rule = registry.get(str(f.get("rule_id")))
    rule_quote = str(f.get("rule_quote", "")).strip()
    return (lines is not None and isinstance(line, int) and 0 < line <= len(lines)
            and bool(quote) and quote in lines[line - 1]
            and rule is not None and bool(rule_quote) and rule_quote in rule["text"])


def ladder(state_path: Path | None, rules_this_round: set[str], write: bool) -> tuple[str, dict[str, str]]:
    if state_path is None:
        return Verdict.NOT_RUN.value, {}
    state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.is_file() else {"rule_counts": {}}
    counts = state.setdefault("rule_counts", {})
    escalated = {}
    for rule in sorted(rules_this_round):
        counts[rule] = counts.get(rule, 0) + 1
        if counts[rule] >= ESCALATE_ON_REPEAT:
            escalated[rule] = "ESCALATE_TO_HUMAN"
    if write:
        state_path.write_text(json.dumps(state, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    return "RUN", escalated


def independence(cfg: dict) -> tuple[str, list[str], list[Finding]]:
    author, reviewers = cfg.get("author_family"), cfg.get("reviewers")
    if author is None or not reviewers:
        return Verdict.NOT_RUN.value, [], []
    overlap = sorted(name for name, fam in reviewers.items() if fam == author)
    findings = []
    if cfg.get("independent") is True and overlap:
        findings.append(Finding("config.toml", 1, core.line_hash(str(overlap)), "not_independent",
                                f"config declares independent = true but {overlap} share the author's family {author!r}"))
    return "RUN", overlap, findings


def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    opts = opts or {}
    cfg = core.gate_section(config, GATE_ID)
    if not opts.get("findings_dir"):
        return core.not_run(GATE_ID, KIND, BLIND_SPOTS, "no --findings-dir given: nothing to validate")
    if len(scope) == 0:
        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)
    with open(scope.root / cfg["rule_registry"], "rb") as fh:
        registry = tomllib.load(fh).get("rules", {})
    reviews = [json.loads((scope.root / rel).read_text(encoding="utf-8")) for rel in scope.files]
    valid, dropped = {}, {}
    for rv in reviews:
        ok = [f for f in rv.get("findings", []) if citation_valid(scope.root, rv["commit"], f, registry)]
        dropped[rv["reviewer"]] = len(rv.get("findings", [])) - len(ok)
        valid.update({f["id"]: (rv["reviewer"], f) for f in ok})
    confirmed = {v["finding_id"] for rv in reviews for v in rv.get("verdicts", [])
                 if v.get("verdict") == "confirm" and v.get("finding_id") in valid
                 and valid[v["finding_id"]][0] != rv["reviewer"]}
    findings = [Finding(f["file"], f["line"], core.line_hash(f["quote"]), f["rule_id"],
                        f"[{who}, corroborated] {f.get('message', '')}") for fid, (who, f) in sorted(valid.items())
                if fid in confirmed]
    state = Path(opts["state"]) if opts.get("state") else None
    if state is not None and not state.is_absolute():
        state = scope.root / state
    ladder_state, escalation = ladder(state, {f.rule for f in findings}, not opts.get("readonly_state"))
    ind_state, overlap, ind_findings = independence(cfg)
    findings += ind_findings
    verdict = Verdict.VIOLATED if findings or escalation else Verdict.SATISFIED
    return GateResult(GATE_ID, verdict, KIND, findings=findings, scope_size=len(scope), blind_spots=BLIND_SPOTS,
                      sub_checks={"validation": "RUN", "corroboration": "RUN",
                                  "escalation_ladder": ladder_state, "independence": ind_state},
                      details={"dropped": dropped, "reported": len(findings) - len(ind_findings),
                               "uncorroborated": len(valid) - len(confirmed), "escalation": escalation,
                               "family_overlap": overlap})


def run_canary(canary_dir: Path) -> GateResult:
    config, opts = core.canary_setup(canary_dir)
    return run(config, review_scope(canary_dir, config, opts), opts)


def _args(ap) -> None:
    ap.add_argument("--findings-dir", dest="findings_dir", help="directory of per-reviewer JSON files")
    ap.add_argument("--state", help="escalation ladder state file, carried across rounds within a PR")


def _scope(root: Path, config: dict, args) -> Scope:
    return review_scope(root, config, vars(args))


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, run, __doc__, add_args=_args, scope_fn=_scope))
