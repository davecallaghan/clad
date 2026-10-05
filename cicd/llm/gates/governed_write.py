"""Gate 2: governed_write (grade reversion at the write; kind = deciding).

Exists to catch: a governed value written without passing the write gate, a write gate that
can be skipped, and a quarantine nobody is alarmed by. Evidence (constraint-reversion,
"Detection surfaces", Group 1 items 1-2): the write gate's contract defaulted to None and the
production caller omitted it, so the system's largest table of governed values was ungated for
the life of the programme; "The asymmetry": quarantine without an alarm is quiet data loss.

Checks: (1) AST call-site scan: every call to a configured writer whose target is a contract
target is preceded, in the same function, by `write_gate(..., <same target>)`; targets come from
the contract and call sites from the code, never from a list. (2) Every `def write_gate` takes
`contract` positionally with no default. (3) Quarantine alarm: a non-zero count in the run
manifest is VIOLATED unless declared in config with a source (NOT_RUN without a manifest).
"""

from __future__ import annotations

import ast
import inspect
import json
import re
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope
from cicd.llm.governed import load_contract, write_gate

GATE_ID = "governed_write"
KIND = "deciding"
BLIND_SPOTS = [
    "writes whose target the AST cannot resolve (dynamic dispatch); counted in details.unresolved_writes",
    "writes made through functions not listed in writer_functions",
    "write_gate's runtime behaviour (covered by its in-gate canary and tests, not by this scan)",
]


def _str_const(node: ast.AST | None, consts: dict[str, str]) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.Name):
        return consts.get(node.id)
    return None


def _module_consts(tree: ast.Module) -> dict[str, str]:
    out = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            t = node.targets[0]
            if isinstance(t, ast.Name) and isinstance(node.value, ast.Constant) and isinstance(node.value.value, str):
                out[t.id] = node.value.value
    return out


def _callee(call: ast.Call) -> str | None:
    f = call.func
    return f.attr if isinstance(f, ast.Attribute) else f.id if isinstance(f, ast.Name) else None


def _gate_target(call: ast.Call, consts: dict[str, str]) -> str | None:
    for kw in call.keywords:
        if kw.arg == "target":
            return _str_const(kw.value, consts)
    # The target's position is read from write_gate's own signature, not written down here.
    i = list(inspect.signature(write_gate).parameters).index("target")
    return _str_const(call.args[i], consts) if len(call.args) > i else None


def _governed(name: str, targets: set[str]) -> str | None:
    for t in targets:
        if name == t or name.endswith("." + t):
            return t
    return None


def _scopes(tree: ast.Module):
    """The module body and every function body: the unit within which a write must be gated."""
    yield tree
    yield from (n for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)))


def _own_calls(scope_node):
    """Calls inside this scope, not inside nested function definitions."""
    stack = list(ast.iter_child_nodes(scope_node))
    while stack:
        n = stack.pop()
        # A nested function is its own scope, visited separately by _scopes.
        if not isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)):
            if isinstance(n, ast.Call):
                yield n
            stack.extend(ast.iter_child_nodes(n))


# SQL that writes a table: INSERT INTO / INSERT OVERWRITE [TABLE] / MERGE INTO <name>.
_SQL_WRITE = re.compile(r"\b(?:INSERT\s+(?:INTO|OVERWRITE)(?:\s+TABLE)?|MERGE\s+INTO)\s+([`\w.]+)", re.IGNORECASE)


def sql_writes(rel: str, tree: ast.Module, lines: list[str], targets: set[str]) -> list[Finding]:
    """A governed table written by SQL text: rows that never pass through write_gate."""
    out = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            for m in _SQL_WRITE.finditer(node.value):
                t = _governed(m.group(1).replace("`", ""), targets)
                if t is not None:
                    out.append(Finding(rel, node.lineno, core.line_hash(lines[node.lineno - 1]), "sql_write",
                                       f"SQL writes governed target {t!r} directly; its rows bypass write_gate"))
    return out


def _gate_inputs(calls: list[ast.Call]) -> set[str]:
    """Names passed to write_gate as its rows: writing these afterwards writes the unchecked rows."""
    return {c.args[0].id for c in calls if _callee(c) == "write_gate" and c.args and isinstance(c.args[0], ast.Name)}


def scan_calls(rel: str, tree: ast.Module, lines: list[str], targets: set[str], writers: list[str]):
    consts = _module_consts(tree)
    findings, gated, unresolved = [], 0, 0
    for node in _scopes(tree):
        calls = sorted(_own_calls(node), key=lambda c: (c.lineno, c.col_offset))
        gated_targets = {_gate_target(c, consts) for c in calls if _callee(c) == "write_gate"}
        raw = _gate_inputs(calls)
        for c in (c for c in calls if _callee(c) in writers):
            name = _str_const(c.args[0], consts) if c.args else None
            t = _governed(name, targets) if name is not None else None
            if name is None:
                unresolved += 1
            elif t is not None and t in gated_targets and any(isinstance(a, ast.Name) and a.id in raw for a in c.args):
                findings.append(Finding(rel, c.lineno, core.line_hash(lines[c.lineno - 1]), "raw_rows_written",
                                        f"write to {t!r} passes the rows given to write_gate, not its accepted rows"))
            elif t is not None and t in gated_targets:
                gated += 1
            elif t is not None:
                findings.append(Finding(rel, c.lineno, core.line_hash(lines[c.lineno - 1]), "ungated_write",
                                        f"write to governed target {t!r} does not go through write_gate in this function"))
    return findings, gated, unresolved


def check_signatures(rel: str, tree: ast.Module, lines: list[str]) -> tuple[list[Finding], int]:
    findings, defs = [], 0
    defs_found = [n for n in ast.walk(tree)
                  if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name == "write_gate"]
    for node in defs_found:
        defs += 1
        a = node.args
        positional = [*a.posonlyargs, *a.args]
        names = [p.arg for p in positional]
        h = core.line_hash(lines[node.lineno - 1])
        if "contract" in names:
            first_default = len(positional) - len(a.defaults)
            if names.index("contract") >= first_default:
                findings.append(Finding(rel, node.lineno, h, "contract_has_default",
                                        "write_gate's contract parameter has a default: a caller can omit it"))
        elif "contract" in [k.arg for k in a.kwonlyargs]:
            findings.append(Finding(rel, node.lineno, h, "contract_not_positional",
                                    "write_gate's contract parameter is keyword-only"))
        else:
            findings.append(Finding(rel, node.lineno, h, "contract_missing",
                                    "write_gate takes no contract parameter"))
    return findings, defs


def quarantine_alarm(root: Path, cfg: dict) -> tuple[str, list[Finding]]:
    manifest = cfg.get("quarantine_manifest")
    if not manifest:
        return Verdict.NOT_RUN.value, []
    counts = json.loads((root / manifest).read_text(encoding="utf-8")).get("quarantine", {})
    expected = cfg.get("expected_quarantine", {})
    findings = []
    for target, n in sorted(counts.items()):
        decl = expected.get(target, {})
        declared = decl.get("source") and decl.get("count") == n
        if n > 0 and not declared:
            findings.append(Finding(manifest, 1, core.line_hash(f"{target}={n}"), "quarantine_nonzero",
                                    f"{n} row(s) quarantined for {target!r} and no count is declared with a source"))
    return "RUN", findings


def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    cfg = core.gate_section(config, GATE_ID)
    contract = load_contract(scope.root / cfg["contract"])
    targets = set(contract.get("targets", {}))
    if not targets or len(scope) == 0:
        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)
    findings, gated, unresolved, defs = [], 0, 0, 0
    for rel in scope.files:
        text = (scope.root / rel).read_text(encoding="utf-8")
        tree, lines = ast.parse(text), text.splitlines()
        f1, g1, u1 = scan_calls(rel, tree, lines, targets, cfg["writer_functions"])
        f1 += sql_writes(rel, tree, lines, targets)
        f2, d2 = check_signatures(rel, tree, lines)
        findings += f1 + f2
        gated, unresolved, defs = gated + g1, unresolved + u1, defs + d2
    if defs == 0:
        findings.append(Finding(cfg["contract"], 1, core.line_hash(""), "write_gate_missing",
                                "the contract governs targets but no write_gate is defined in scope"))
    alarm_state, alarm = quarantine_alarm(scope.root, cfg)
    findings += alarm
    return GateResult(GATE_ID, Verdict.VIOLATED if findings else Verdict.SATISFIED, KIND,
                      findings=findings, scope_size=len(scope), blind_spots=BLIND_SPOTS,
                      sub_checks={"call_sites": "RUN", "signature": "RUN", "quarantine_alarm": alarm_state},
                      details={"targets": sorted(targets), "gated_writes": gated,
                               "unresolved_writes": unresolved, "write_gate_defs": defs})


def run_canary(canary_dir: Path) -> GateResult:
    config, opts = core.canary_setup(canary_dir)
    cfg = core.gate_section(config, GATE_ID)
    return run(config, core.derive_scope(canary_dir, cfg["include"], cfg.get("exclude", [])), opts)


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, run, __doc__))
