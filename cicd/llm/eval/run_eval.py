"""Guidance evaluation harness (guidance section 4). Built, NOT run: running needs owner approval.

Runs Claude Code headless (`claude -p`) on each owner-supplied task in a throwaway git worktree,
`repeats` times per arm, and records each gate's first verdict per run in the ledger:
- A: no CLAUDE.md, skills or hooks (files removed from the worktree, and `--safe-mode`, which the
  CLI reference says disables CLAUDE.md, skills, hooks and other customisations);
- B: CLAUDE.md and skills, no hooks (hooks removed from the worktree's .claude/settings.json);
- C: CLAUDE.md, skills and hooks.
B and C pass `--setting-sources project,local` so user-level hooks and settings do not leak in.
Declared blind spot: user-level CLAUDE.md and ~/.claude/skills may still load in B and C (they
cannot be disabled without also disabling the project's), equally in both arms.

The report gives, per arm and per gate, first-attempt pass count / total (no rates, nothing
rounded or extrapolated) and, when max_attempts is set, the attempts needed to reach a pass.

    python -m cicd.llm.eval.run_eval --plan                    # what would run; calls nothing
    python -m cicd.llm.eval.run_eval --run --owner-approved    # spends model usage
    python -m cicd.llm.eval.run_eval --report                  # summarise the ledger
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import tomllib
from collections import defaultdict
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core, preflight
from cicd.llm.core import Scope
from cicd.llm.gates import completion_evidence, safeguard_removal

HERE = Path(__file__).resolve().parent
LEDGER = core.KIT_DIR / "ledger" / "eval.jsonl"
REQUIRED = ("permission_mode", "max_budget_usd")


class EvalRefused(Exception):
    pass


def load_settings(path: Path | None = None) -> dict:
    with open(path or HERE / "eval.toml", "rb") as fh:
        return tomllib.load(fh)


def tasks(tasks_dir: Path) -> dict[str, str]:
    return {p.stem: p.read_text(encoding="utf-8") for p in sorted(tasks_dir.glob("*.md")) if p.name != "README.md"}


def prepare_arm(worktree: Path, arm: str) -> None:
    """Remove from the worktree what the arm must not have (derived from the tree, not a list).

    Every arm also loses any completion claim already in the tree: the session must write its own,
    or completion_evidence would score an earlier task's claim (found in a smoke run, 2026-10-05).
    """
    claim = worktree / core.load_config()["hooks"]["completion"]
    if claim.is_file():
        claim.unlink()
    if arm == "A":
        for md in worktree.rglob("CLAUDE.md"):
            md.unlink()
        skills = worktree / ".claude" / "skills"
        if skills.exists():
            shutil.rmtree(skills)
    if arm in ("A", "B"):
        settings = worktree / ".claude" / "settings.json"
        if settings.is_file():
            data = json.loads(settings.read_text())
            data.pop("hooks", None)
            settings.write_text(json.dumps(data, indent=2) + "\n")


def claude_argv(arm: str, prompt: str, s: dict, resume: bool = False) -> list[str]:
    argv = [os.environ.get("CICD_LLM_CLAUDE", "claude"), "-p", prompt, "--output-format", "json",
            "--permission-mode", s["permission_mode"], "--max-budget-usd", str(s["max_budget_usd"])]
    argv += ["--safe-mode"] if arm == "A" else ["--setting-sources", "project,local"]
    if s.get("allowed_tools"):
        argv += ["--allowedTools", *s["allowed_tools"]]
    if s.get("disallowed_tools"):
        argv += ["--disallowedTools", *s["disallowed_tools"]]
    if s.get("model"):
        argv += ["--model", s["model"]]
    if resume:
        argv += ["--continue"]
    return argv


def evaluate(worktree: Path, base: str) -> dict[str, str]:
    """Each gate's verdict on the worktree after a run. Gates that need inputs a run cannot
    produce (review_findings) are recorded as NOT_RUN."""
    config = core.load_config()
    verdicts: dict[str, str] = {}
    changed = [p for p in core.git(worktree, "diff", "--name-only", base).split() +
               core.git(worktree, "ls-files", "--others", "--exclude-standard").split() if (worktree / p).exists()]
    _, report = preflight.run_paths(worktree, [str(worktree / p) for p in changed], config)
    for gate, r in report.get("gates", {}).items():
        verdicts[gate] = r if r == preflight.NOT_APPLICABLE else r["verdict"]
    opts = {"base": base, "head": "WORKTREE"}
    verdicts["safeguard_removal"] = safeguard_removal.run(
        config, safeguard_removal.diff_scope(worktree, config, opts), opts).verdict.value
    rel = config["hooks"]["completion"]
    claim = Scope(worktree, [rel] if (worktree / rel).is_file() else [])
    verdicts["completion_evidence"] = completion_evidence.run(config, claim, {}).verdict.value
    verdicts["review_findings"] = Verdict.NOT_RUN.value
    return verdicts


def plan(tasks_dir: Path, s: dict) -> list[tuple[str, str, int]]:
    return [(t, arm, n) for t in tasks(tasks_dir) for arm in s["arms"] for n in range(1, s["repeats"] + 1)]


def run_one(root: Path, task: str, prompt: str, arm: str, n: int, s: dict) -> dict:
    base = core.git(root, "rev-parse", "HEAD").strip()
    with tempfile.TemporaryDirectory() as tmp:
        wt = Path(tmp) / "wt"
        branch = f"eval/{task}/{arm}/{n}"
        core.git(root, "worktree", "add", "-q", "-b", branch, str(wt), base)
        try:
            prepare_arm(wt, arm)
            core.git(wt, "commit", "-q", "-am", f"eval arm {arm}", "--allow-empty")
            arm_base = core.git(wt, "rev-parse", "HEAD").strip()
            attempts, first, passed_at = s.get("max_attempts") or 1, None, {}
            for attempt in range(1, attempts + 1):
                # The worktree has no .venv-llm (it is gitignored); arm C's hooks use the repository's.
                env = {**os.environ, "CICD_LLM_PYTHON": os.environ.get("CICD_LLM_PYTHON", sys.executable)}
                proc = subprocess.run(claude_argv(arm, prompt, s, resume=attempt > 1), cwd=wt,
                                      capture_output=True, text=True, env=env)
                verdicts = evaluate(wt, arm_base)
                first = first or {"claude_exit": proc.returncode, "verdicts": verdicts}
                for g, v in verdicts.items():
                    if v == Verdict.SATISFIED.value and g not in passed_at:
                        passed_at[g] = attempt
                failing = {g: v for g, v in verdicts.items() if v not in (Verdict.SATISFIED.value, preflight.NOT_APPLICABLE)}
                prompt = f"The LLM gates report: {json.dumps(failing)}. Fix the findings."
                if not failing:
                    break
        finally:
            core.git(root, "worktree", "remove", "--force", str(wt))
            core.git(root, "branch", "-D", branch)
    return {"task": task, "arm": arm, "repeat": n, "base": base, "first": first,
            "attempts_to_pass": passed_at if s.get("max_attempts") else Verdict.NOT_RUN.value}


def report(records: list[dict]) -> dict:
    out: dict = defaultdict(lambda: defaultdict(lambda: {"first_attempt_pass": 0, "total": 0, "attempts_to_pass": []}))
    for r in records:
        for gate, v in r["first"]["verdicts"].items():
            cell = out[r["arm"]][gate]
            cell["total"] += 1
            cell["first_attempt_pass"] += v == Verdict.SATISFIED.value
            if isinstance(r["attempts_to_pass"], dict) and gate in r["attempts_to_pass"]:
                cell["attempts_to_pass"].append(r["attempts_to_pass"][gate])
    return {arm: {g: {**c, "first_attempt": f"{c['first_attempt_pass']}/{c['total']}"} for g, c in gates.items()}
            for arm, gates in sorted(out.items())}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--plan", action="store_true")
    mode.add_argument("--run", action="store_true")
    mode.add_argument("--report", action="store_true")
    ap.add_argument("--owner-approved", action="store_true", help="the owner approved spending model usage")
    ap.add_argument("--tasks-dir", type=Path, default=HERE / "tasks")
    ap.add_argument("--settings", type=Path, default=HERE / "eval.toml")
    a = ap.parse_args()
    s = load_settings(a.settings)
    if a.report:
        records = [json.loads(line) for line in LEDGER.read_text().splitlines()] if LEDGER.is_file() else []
        print(json.dumps(report(records), indent=1, sort_keys=True))
        return 0 if records else Verdict.NOT_RUN.exit_code
    runs = plan(a.tasks_dir, s)
    if a.plan or not runs:
        print(json.dumps({"runs": runs, "missing_settings": [k for k in REQUIRED if k not in s]}, indent=1))
        return 0 if runs else Verdict.NOT_RUN.exit_code
    if not a.owner_approved:
        print("REFUSED: running costs model usage and needs the owner's approval (--owner-approved).", file=sys.stderr)
        return Verdict.NOT_RUN.exit_code
    missing = [k for k in REQUIRED if k not in s]
    if missing:
        print(f"REFUSED: set {missing} in cicd/llm/eval/eval.toml; the builder does not choose them.", file=sys.stderr)
        return Verdict.NOT_RUN.exit_code
    root, prompts = core.repo_root(), tasks(a.tasks_dir)
    for task, arm, n in runs:
        LEDGER.parent.mkdir(parents=True, exist_ok=True)
        with open(LEDGER, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(run_one(root, task, prompts[task], arm, n, s), sort_keys=True) + "\n")
    print(json.dumps(report([json.loads(x) for x in LEDGER.read_text().splitlines()]), indent=1, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
