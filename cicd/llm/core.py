"""Shared gate contract for cicd/llm (spec section 2).

Every gate in `cicd/llm/gates/` returns a `GateResult` from `run(config, scope)` and is runnable
as `python -m cicd.llm.gates.<name>`. This module owns the parts that must not differ between
gates: scope derivation (from the artifact, never a list), the empty-enumeration rule, config
loading, the JSON verdict and the ledger.

Two rules from the countermeasures section are enforced here rather than trusted to each gate:
an error inside a gate becomes NOT_RUN carrying the error message (never a pass, never a crash
that CI might read as "nothing found"), and an empty derived scope is INEXPRESSIBLE.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import functools
import hashlib
import json
import os
import re
import subprocess
import sys
import tomllib
from dataclasses import asdict, dataclass, field
from pathlib import Path

from cicd.lib.verdict import Verdict

KIT_DIR = Path(__file__).resolve().parent
DEFAULT_CONFIG = KIT_DIR / "config.toml"
DEFAULT_LEDGER = KIT_DIR / "ledger" / "ledger.jsonl"
EMPTY_ENUMERATION = "empty enumeration is not a clean surface"


class GateError(Exception):
    """An error a gate raises deliberately; the runner turns it into NOT_RUN."""


# The specific failures a gate can hit while reading the artifact. Catching exactly these (and
# not `Exception`) keeps a programming error in a gate loud: it crashes the CLI with a traceback
# and a non-zero exit instead of being reported as a verdict.
READ_ERRORS: tuple[type[BaseException], ...] = (
    OSError,
    UnicodeDecodeError,
    SyntaxError,
    ValueError,  # includes json.JSONDecodeError and tomllib.TOMLDecodeError
    subprocess.CalledProcessError,
    GateError,
)


@dataclass
class Finding:
    file: str
    line: int
    line_hash: str
    rule: str
    message: str
    likely_invented: bool | None = None
    tags: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        d = asdict(self)
        if d["likely_invented"] is None:
            del d["likely_invented"]
        if not d["tags"]:
            del d["tags"]
        return d


@dataclass
class GateResult:
    gate: str
    verdict: Verdict
    kind: str  # "deciding" or "proxy"
    findings: list[Finding] = field(default_factory=list)
    scope_size: int = 0
    blind_spots: list[str] = field(default_factory=list)
    sub_checks: dict[str, str] = field(default_factory=dict)  # name -> Verdict.value
    notes: list[str] = field(default_factory=list)
    details: dict = field(default_factory=dict)

    def to_dict(self, root: Path | None = None) -> dict:
        return {
            "gate": self.gate,
            "verdict": self.verdict.value,
            "kind": self.kind,
            "findings": [f.to_dict() for f in self.findings],
            "scope_size": self.scope_size,
            "blind_spots": list(self.blind_spots),
            "sub_checks": dict(self.sub_checks),
            "notes": list(self.notes),
            "details": self.details,
            "tree_hash": tree_hash(root) if root is not None else None,
            "timestamp": _dt.datetime.now(_dt.timezone.utc).isoformat(timespec="seconds"),
        }


@dataclass
class Scope:
    root: Path
    files: list[str]  # repo-relative POSIX paths

    def __len__(self) -> int:
        return len(self.files)


def line_hash(text: str) -> str:
    """Content hash of one source line, whitespace-trimmed so re-indentation does not move it."""
    return "sha256:" + hashlib.sha256(text.strip().encode("utf-8")).hexdigest()


def load_config(path: Path | None = None) -> dict:
    p = path or Path(os.environ.get("CICD_LLM_CONFIG", DEFAULT_CONFIG))
    with open(p, "rb") as fh:
        return tomllib.load(fh)


def git(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=True).stdout


def repo_root(start: Path | None = None) -> Path:
    return Path(git(start or Path.cwd(), "rev-parse", "--show-toplevel").strip())


def tree_hash(root: Path) -> str | None:
    """HEAD's tree hash, or None outside a git repository (recorded, not invented)."""
    r = subprocess.run(["git", "rev-parse", "HEAD^{tree}"], cwd=root, capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None


@functools.lru_cache(maxsize=None)
def _glob_regex(pattern: str) -> re.Pattern:
    """Translate a path glob: `**/` is zero or more directories, `*` and `?` stay in one segment."""
    out, i = [], 0
    while i < len(pattern):
        if pattern.startswith("**/", i):
            out.append("(?:[^/]+/)*")
            i += len("**/")
        elif pattern.startswith("**", i):
            out.append(".*")
            i += len("**")
        elif pattern[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pattern[i] == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return re.compile("".join(out) + r"\Z")


def glob_match(path: str, pattern: str) -> bool:
    return _glob_regex(pattern).match(path) is not None


def derive_scope(root: Path, include: list[str], exclude: list[str]) -> Scope:
    """Files tracked by git under `root`, filtered by globs. Never a literal file list."""
    # --others --exclude-standard: untracked but not ignored files count too, so a new file is
    # in scope before it is committed (what preflight checks) and ignored build output is not.
    tracked = [p for p in git(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard")
               .split("\0") if p]
    tracked = sorted(set(tracked))
    files = [
        p for p in tracked
        if any(glob_match(p, g) for g in include) and not any(glob_match(p, g) for g in exclude)
        and (root / p).is_file()
    ]
    return Scope(root=root, files=sorted(files))


def empty_scope(gate: str, kind: str, blind_spots: list[str]) -> GateResult:
    return GateResult(gate, Verdict.INEXPRESSIBLE, kind, blind_spots=blind_spots,
                      notes=[EMPTY_ENUMERATION])


def not_run(gate: str, kind: str, blind_spots: list[str], message: str) -> GateResult:
    return GateResult(gate, Verdict.NOT_RUN, kind, blind_spots=blind_spots, notes=[message])


def append_ledger(record: dict) -> None:
    path = Path(os.environ.get("CICD_LLM_LEDGER", DEFAULT_LEDGER))
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, sort_keys=True) + "\n")


def gate_section(config: dict, gate: str) -> dict:
    section = config.get("gates", {}).get(gate)
    if section is None:
        raise GateError(f"config has no [gates.{gate}] section")
    return section


def base_parser(description: str) -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=description)
    ap.add_argument("--root", type=Path, help="repository to check (default: the enclosing git repo)")
    ap.add_argument("--config", type=Path, help="config file (default: cicd/llm/config.toml)")
    ap.add_argument("--no-ledger", action="store_true", help="do not append to the ledger")
    return ap


def cli(gate: str, kind: str, blind_spots: list[str], run, description: str,
        add_args=None, scope_fn=None) -> int:
    """Shared CLI: parse, derive scope, run, print JSON, append ledger, exit with the verdict code.

    Every gate is `run(config, scope, opts=None)`; `opts` carries gate-specific CLI options
    (for example `--base`). `scope_fn(root, config, args)` derives the gate's scope; it defaults
    to the gate's include/exclude globs. Specific read errors become NOT_RUN with their message.
    """
    ap = base_parser(description)
    if add_args:
        add_args(ap)
    args = ap.parse_args()
    root = (args.root or repo_root()).resolve()
    try:
        config = load_config(args.config)
        if scope_fn is not None:
            scope = scope_fn(root, config, args)
        else:
            section = gate_section(config, gate)
            scope = derive_scope(root, section["include"], section.get("exclude", []))
        result = run(config, scope, vars(args))
    except READ_ERRORS as exc:
        result = not_run(gate, kind, blind_spots, f"{type(exc).__name__}: {exc}")
    record = result.to_dict(root)
    print(json.dumps(record, indent=1, sort_keys=True))
    if not args.no_ledger:
        append_ledger(record)
    return result.verdict.exit_code


def main_exit(code: int) -> None:
    sys.exit(code)


def deep_merge(base: dict, over: dict) -> dict:
    out = dict(base)
    for k, v in over.items():
        out[k] = deep_merge(out[k], v) if isinstance(v, dict) and isinstance(out.get(k), dict) else v
    return out


def canary_setup(canary_dir: Path) -> tuple[dict, dict]:
    """Config and options for running a gate against one canary directory.

    The kit config is used unchanged except for what the canary's own `canary.toml` overrides
    under `[config]`; `[opts]` carries CLI-equivalent options. Paths resolve against the canary.
    """
    config = load_config()
    opts: dict = {}
    spec = canary_dir / "canary.toml"
    if spec.is_file():
        with open(spec, "rb") as fh:
            data = tomllib.load(fh)
        config = deep_merge(config, data.get("config", {}))
        opts = data.get("opts", {})
    return config, opts
