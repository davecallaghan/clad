"""Gate 1: invented_limits (bound reversion at source; kind = proxy).

Exists to catch: a limit the model supplied that makes the system process less than all of its
input. Evidence (constraint-reversion, "Bound reversion"): fourteen invented limits across ten
pull requests in 16.9 hours with the prohibition in context; two were job-manifest parameters no
guard read, and survived eight PRs; all twenty-six substituted values were conventional
("Test 1: value conventionality"); corrections replaced a bound with a larger one ("Test 2:
escalation direction").

Flags: (1) limit-named keys/parameters with numeric literal values, (2) SQL `LIMIT n`,
(3) constant slice bounds, (4) `.head/.limit/.take(n)`, (5) timeout fields in job manifests,
(6) budgets below `model_capacity` (NOT_RUN when that is unset). Python is read by AST, YAML/JSON
by a parser, SQL/TS/Scala by regex (tagged `proxy:regex`).
"""

from __future__ import annotations

import ast
import re
import subprocess
import tomllib
from collections import Counter
from pathlib import Path

import yaml

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope

GATE_ID = "invented_limits"
KIND = "proxy"
BLIND_SPOTS = [
    "limits computed at runtime",
    "limits inside external services",
    "limits stated in prose",
]
ALLOWLIST_FIELDS = ("file", "line_hash", "value", "justification", "source", "approver")
_NUMBER = r"-?\d+(?:\.\d+)?"
_REGEX_KEY = re.compile(r"\b([A-Za-z_]\w*)\s*(?::\s*[\w\[\]]+\s*)?[:=]\s*(" + _NUMBER + r")\b")
# LIMIT n; T-SQL TOP n / TOP (n); ANSI FETCH FIRST n ROWS.
_SQL_LIMIT = re.compile(r"\b(?:LIMIT\s+|TOP\s*\(?\s*|FETCH\s+FIRST\s+)(\d+)\b", re.IGNORECASE)
_REGEX_SUFFIXES = {".sql", ".ts", ".scala"}
_IDENTIFIER = re.compile(r"[A-Za-z_][\w.]*")  # a setting name, not prose that happens to start with max_
_YAML_NUMERIC_TAGS = {"tag:yaml.org,2002:int", "tag:yaml.org,2002:float"}


# ---- helpers ----------------------------------------------------------------------------------

def snake(name: str) -> str:
    return re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", name).lower()


def name_matches(name: str, patterns: list[str]) -> bool:
    return any(core.glob_match(snake(name), p) for p in patterns)


def numeric_literal(node: ast.AST) -> float | int | None:
    if isinstance(node, ast.Constant) and type(node.value) in (int, float):
        return node.value
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
        inner = numeric_literal(node.operand)
        return -inner if inner is not None else None
    return None


def _numeric_string(text: str) -> float | int | None:
    return parse_number(text.strip()) if re.fullmatch(_NUMBER, text.strip()) else None


def parse_number(text: str) -> float | int:
    return float(text) if "." in text else int(text)


def likely_invented(value: float | int, cfg: dict) -> bool:
    """Round number by definition: a positive integer multiple of the base or of a time unit."""
    if value != int(value) or value <= 0:
        return False
    v = int(value)
    units = [cfg["round_base"], *cfg["time_unit_seconds"]]
    return any(v % u == 0 and v >= u for u in units)


class _Collector:
    """Accumulates raw findings for one file; the verdict is decided once, at the end."""

    def __init__(self, rel: str, lines: list[str], li_cfg: dict, tags: list[str]):
        self.rel, self.lines, self.li_cfg, self.tags = rel, lines, li_cfg, tags
        self.items: list[tuple[Finding, float | int | None]] = []

    def add(self, line: int, rule: str, message: str, value=None) -> None:
        text = self.lines[line - 1] if 0 < line <= len(self.lines) else ""
        f = Finding(self.rel, line, core.line_hash(text), rule, message,
                    likely_invented=None if value is None else likely_invented(value, self.li_cfg),
                    tags=list(self.tags))
        self.items.append((f, value))


# ---- Python (AST) -----------------------------------------------------------------------------

class _PyScanner(ast.NodeVisitor):
    def __init__(self, out: _Collector, cfg: dict):
        self.out, self.cfg = out, cfg

    def _key(self, name: str, value_node: ast.AST, line: int) -> None:
        v = numeric_literal(value_node)
        if v is None:
            return
        if name_matches(name, self.cfg["key_patterns"]):
            self.out.add(line, "limit_key", f"limit-named `{name}` has literal value {v}", v)
        self._budget(name, v, line)

    def _budget(self, name: str, v, line: int) -> None:
        cap = self.cfg.get("model_capacity")
        if cap is not None and name_matches(name, self.cfg["budget_patterns"]) and v < cap:
            self.out.add(line, "budget_below_capacity",
                         f"budget `{name}` = {v} is below model_capacity {cap}", v)

    def visit_Assign(self, node: ast.Assign) -> None:
        for t in node.targets:
            name = t.id if isinstance(t, ast.Name) else t.attr if isinstance(t, ast.Attribute) else None
            if name:
                self._key(name, node.value, node.lineno)
        self.generic_visit(node)

    def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
        if node.value is not None and isinstance(node.target, ast.Name):
            self._key(node.target.id, node.value, node.lineno)
        self.generic_visit(node)

    def visit_keyword(self, node: ast.keyword) -> None:
        if node.arg:
            self._key(node.arg, node.value, node.value.lineno)
        self.generic_visit(node)

    def _defaults(self, node) -> None:
        a = node.args
        positional = [*a.posonlyargs, *a.args]
        for arg, default in zip(positional[len(positional) - len(a.defaults):], a.defaults):
            self._key(arg.arg, default, default.lineno)
        for arg, default in zip(a.kwonlyargs, a.kw_defaults):
            if default is not None:
                self._key(arg.arg, default, default.lineno)

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self._defaults(node)
        self.generic_visit(node)

    visit_AsyncFunctionDef = visit_FunctionDef

    def visit_Dict(self, node: ast.Dict) -> None:
        for k, v in zip(node.keys, node.values):
            if isinstance(k, ast.Constant) and isinstance(k.value, str):
                self._key(k.value, v, v.lineno)
        self.generic_visit(node)

    def visit_Subscript(self, node: ast.Subscript) -> None:
        s = node.slice
        if isinstance(s, ast.Slice):
            upper = numeric_literal(s.upper) if s.upper is not None else None
            lower = numeric_literal(s.lower) if s.lower is not None else None
            bound = upper if upper is not None else lower if lower not in (None, 0) else None
            if bound is not None:
                self.out.add(node.lineno, "constant_slice", f"slice with constant bound {bound}", bound)
        self.generic_visit(node)

    def visit_Call(self, node: ast.Call) -> None:
        f = node.func
        name = f.attr if isinstance(f, ast.Attribute) else f.id if isinstance(f, ast.Name) else None
        bounds = [v for a in node.args if (v := numeric_literal(a)) is not None and v != 0]
        if isinstance(f, ast.Attribute) and name in self.cfg["truncating_methods"] and bounds:
            self.out.add(node.lineno, "truncating_call", f".{name}(...{bounds[-1]}) with a literal bound", bounds[-1])
        if name in self.cfg.get("bounding_functions", []) and bounds:
            self.out.add(node.lineno, "bounding_call", f"{name}(...{bounds[-1]}) with a literal bound", bounds[-1])
        self._literal_default(node)
        self.generic_visit(node)

    def _literal_default(self, node: ast.Call) -> None:
        """A limit-named key read with a literal default: `widgets.text("max_x", "20")`, `.get("TIMEOUT", 90)`."""
        keys = [a.value for a in node.args if isinstance(a, ast.Constant) and isinstance(a.value, str)
                and _IDENTIFIER.fullmatch(a.value) and name_matches(a.value, self.cfg["key_patterns"])]
        values = [v for a in node.args if (v := numeric_literal(a)) is not None or
                  (isinstance(a, ast.Constant) and isinstance(a.value, str) and (v := _numeric_string(a.value)) is not None)]
        if keys and values:
            self.out.add(node.lineno, "limit_default",
                         f"limit-named key {keys[0]!r} read with a literal default {values[0]}", values[0])

    def visit_Constant(self, node: ast.Constant) -> None:
        if isinstance(node.value, str):
            for m in _SQL_LIMIT.finditer(node.value):
                self.out.add(node.lineno, "sql_limit", f"SQL LIMIT {m.group(1)} in a string literal",
                             int(m.group(1)))


def scan_python(text: str, out: _Collector, cfg: dict) -> None:
    _PyScanner(out, cfg).visit(ast.parse(text))


# ---- YAML / JSON (parser with line marks) -----------------------------------------------------

def _walk_yaml(node, out: _Collector, cfg: dict, is_manifest: bool) -> None:
    if isinstance(node, yaml.MappingNode):
        for k, v in node.value:
            if isinstance(k, yaml.ScalarNode) and isinstance(v, yaml.ScalarNode) and v.tag in _YAML_NUMERIC_TAGS:
                _yaml_pair(k.value, parse_number(v.value), v.start_mark.line + 1, out, cfg, is_manifest)
            _walk_yaml(v, out, cfg, is_manifest)
    elif isinstance(node, yaml.SequenceNode):
        for item in node.value:
            _walk_yaml(item, out, cfg, is_manifest)


def _yaml_pair(key: str, v, line: int, out: _Collector, cfg: dict, is_manifest: bool) -> None:
    if is_manifest and name_matches(key, cfg["manifest_timeout_patterns"]):
        out.add(line, "manifest_timeout", f"timeout field `{key}` = {v} in a job manifest", v)
    elif name_matches(key, cfg["key_patterns"]):
        out.add(line, "limit_key", f"limit-named `{key}` has literal value {v}", v)
    cap = cfg.get("model_capacity")
    if cap is not None and name_matches(key, cfg["budget_patterns"]) and v < cap:
        out.add(line, "budget_below_capacity", f"budget `{key}` = {v} is below model_capacity {cap}", v)


def scan_structured(text: str, out: _Collector, cfg: dict, is_manifest: bool) -> None:
    for doc in yaml.compose_all(text, Loader=yaml.SafeLoader):
        _walk_yaml(doc, out, cfg, is_manifest)


# ---- SQL / TS / Scala (regex; proxy) ----------------------------------------------------------

def scan_regex(text: str, out: _Collector, cfg: dict, suffix: str) -> None:
    methods = "|".join(re.escape(m) for m in cfg["truncating_methods"])
    trunc = re.compile(r"\.(" + methods + r")\(\s*(" + _NUMBER + r")\s*(?:,\s*(" + _NUMBER + r")\s*)?\)")
    for i, line in enumerate(out.lines, start=1):
        for m in _SQL_LIMIT.finditer(line):
            out.add(i, "sql_limit", f"SQL LIMIT {m.group(1)}", int(m.group(1)))
        if suffix != ".sql":  # SQL has no key assignments or method calls to scan
            _scan_regex_code(line, i, out, cfg, trunc)


def _scan_regex_code(line: str, i: int, out: _Collector, cfg: dict, trunc: re.Pattern) -> None:
    for m in _REGEX_KEY.finditer(line):
        if name_matches(m.group(1), cfg["key_patterns"]):
            v = parse_number(m.group(2))
            out.add(i, "limit_key", f"limit-named `{m.group(1)}` has literal value {v}", v)
    for m in trunc.finditer(line):
        v = parse_number(m.group(3) or m.group(2))
        if v != 0:
            out.add(i, "truncating_call", f".{m.group(1)}(...{v}) with a literal bound", v)


# ---- per-file dispatch ------------------------------------------------------------------------

def scan_text(rel: str, text: str, cfg: dict) -> list[tuple[Finding, float | int | None]]:
    suffix = Path(rel).suffix
    is_regex = suffix in _REGEX_SUFFIXES
    out = _Collector(rel, text.splitlines(), cfg["likely_invented"], ["proxy:regex"] if is_regex else [])
    if suffix == ".py":
        scan_python(text, out, cfg)
    elif suffix in (".yml", ".yaml", ".json"):
        is_manifest = any(core.glob_match(rel, g) for g in cfg["manifest_globs"])
        scan_structured(text, out, cfg, is_manifest)
    elif is_regex:
        scan_regex(text, out, cfg, suffix)
    else:
        raise core.GateError(f"{rel}: no scanner for suffix {suffix!r}; narrow the include globs")
    return out.items


# ---- allowlist and escalation -----------------------------------------------------------------

def load_allowlist(root: Path, cfg: dict) -> tuple[set[tuple[str, str]], list[Finding]]:
    path = root / cfg["allowlist"]
    if not path.is_file():
        return set(), []
    with open(path, "rb") as fh:
        entries = tomllib.load(fh).get("entry", [])
    allowed, problems = set(), []
    for i, e in enumerate(entries, start=1):
        missing = [k for k in ALLOWLIST_FIELDS if e.get(k) in (None, "")]
        if missing:
            problems.append(Finding(cfg["allowlist"], i, core.line_hash(str(e)), "allowlist_invalid",
                                    f"allowlist entry {i} is missing {missing}"))
        else:
            allowed.add((e["file"], e["line_hash"]))
    return allowed, problems


def _signature(f: Finding, line_text: str) -> tuple[str, str, str]:
    return f.file, f.rule, re.sub(_NUMBER, "#", line_text.strip())


def base_values(root: Path, base: str, rel: str, cfg: dict) -> dict[tuple[str, str, str], float | int]:
    shown = subprocess.run(["git", "show", f"{base}:{rel}"], cwd=root, capture_output=True, text=True)
    if shown.returncode != 0:
        return {}  # the file did not exist at base: nothing can have been raised
    lines = shown.stdout.splitlines()
    return {_signature(f, lines[f.line - 1]): v
            for f, v in scan_text(rel, shown.stdout, cfg) if v is not None}


def _mark_escalation(f: Finding, v, prev) -> None:
    if prev is not None and v is not None and v > prev:
        f.tags.append("escalation")
        f.message += f" (escalation: raised from {prev} without an allowlist entry)"


# ---- run ---------------------------------------------------------------------------------------

def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    opts = opts or {}
    cfg = core.gate_section(config, GATE_ID)
    if len(scope) == 0:
        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)
    allowed, findings = load_allowlist(scope.root, cfg)
    unparseable, by_type, n_allowed = [], Counter(), 0
    for rel in scope.files:
        by_type[Path(rel).suffix] += 1
        text = (scope.root / rel).read_text(encoding="utf-8")
        try:
            items = scan_text(rel, text, cfg)
        except (SyntaxError, yaml.YAMLError) as exc:
            unparseable.append(rel)
            items = []
            findings.append(Finding(rel, 1, core.line_hash(""), "unparseable",
                                    f"{rel}: unparseable ({type(exc).__name__}: {exc})"))
        base = base_values(scope.root, opts["base"], rel, cfg) if opts.get("base") and items else {}
        lines = text.splitlines()
        for f, v in items:
            if (f.file, f.line_hash) in allowed:
                n_allowed += 1  # recorded in details["allowlisted"]
            else:
                _mark_escalation(f, v, base.get(_signature(f, lines[f.line - 1])))
                findings.append(f)
    violations = [f for f in findings if f.rule != "unparseable"]
    verdict = Verdict.VIOLATED if violations else Verdict.NOT_RUN if unparseable else Verdict.SATISFIED
    sub = {"budget_below_capacity": "RUN" if cfg.get("model_capacity") is not None else Verdict.NOT_RUN.value,
           "escalation": "RUN" if opts.get("base") else Verdict.NOT_RUN.value}
    return GateResult(GATE_ID, verdict, KIND, findings=findings, scope_size=len(scope),
                      blind_spots=BLIND_SPOTS, sub_checks=sub,
                      details={"files_by_type": dict(sorted(by_type.items())), "allowlisted": n_allowed,
                               "unparseable": unparseable})


def run_canary(canary_dir: Path) -> GateResult:
    config, opts = core.canary_setup(canary_dir)
    cfg = core.gate_section(config, GATE_ID)
    return run(config, core.derive_scope(canary_dir, cfg["include"], cfg.get("exclude", [])), opts)


def _args(ap) -> None:
    ap.add_argument("--base", help="git ref to compare against (escalation mode)")


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, run, __doc__, add_args=_args))
