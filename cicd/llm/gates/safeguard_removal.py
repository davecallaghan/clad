"""Gate 6: safeguard_removal (diff-based; kind = proxy).

Exists to catch: a safeguard removed or neutered. Evidence (constraint-reversion, "Why review
does not catch a control dying"): the AI reviewer caught the three faults that added a
prohibited construct and missed all three that removed or neutered a safeguard (a swallowed
exception, a deleted canary call, a discarded quarantine list). Removal is how a control dies.

Safeguards are identified in code, never by a list: functions decorated `@safeguard`, and lines
marked `# safeguard`. Canary calls are callees matching config `canary_call_patterns`. If no
safeguard exists in scope the gate is VIOLATED: there is nothing for it to protect.

Over `--base`..`--head` (head may be `WORKTREE`), per changed Python file, flags:
(1) a deleted safeguard or canary call (or marker line); (2) a handler broadened to `Exception`,
`BaseException` or bare, or a handler body reduced to `pass`/`continue`; (3) a new `try` around a
safeguard call; (4) a safeguard or `contract` parameter gaining a default; (5) the result of
`write_gate` discarded or bound to `_`; (6) a safeguard call made unreachable (`if False:`, or an
earlier return/raise/continue/break in the same block); (7) deleted assertions, or tests newly
marked skip/xfail. Constructs are compared as multisets keyed by enclosing function and source.
"""

from __future__ import annotations

import ast
import shutil
import subprocess
import tempfile
from collections import Counter, defaultdict
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope

GATE_ID = "safeguard_removal"
KIND = "proxy"
MARKER = "# safeguard"
BLIND_SPOTS = [
    "safeguards that are neither decorated @safeguard nor marked '# safeguard'",
    "semantic neutering inside a safeguard's own body (its logic changed, its calls intact)",
    "non-Python files",
    "a removed safeguard re-added elsewhere under the same source text (multiset comparison)",
]
REMOVED = {"guard_call": "deleted_safeguard_call", "marker": "deleted_safeguard_call",
           "assertion": "assertion_deleted"}
ADDED = {"broad_handler": "broadened_handler", "neutered_handler": "neutered_handler",
         "wrapped": "wrapped_safeguard", "default": "parameter_gained_default",
         "discarded": "write_gate_result_discarded", "unreachable": "unreachable_guard",
         "skip": "test_skipped"}
_BROAD = {"Exception", "BaseException"}
_SKIP_SUFFIXES = (".skip", ".skipif", ".xfail", "importorskip")
_EXITS = (ast.Return, ast.Raise, ast.Continue, ast.Break)


# ---- scope and sources ------------------------------------------------------------------------

def _git(root: Path, *args: str) -> str:
    return core.git(root, *args)


def diff_scope(root: Path, config: dict, opts: dict) -> Scope:
    cfg = core.gate_section(config, GATE_ID)
    base, head = opts.get("base"), opts.get("head") or "HEAD"
    if not base:
        return Scope(root=root, files=[])
    if head == "WORKTREE":
        names = _git(root, "diff", "--name-only", base).split() + \
            _git(root, "ls-files", "--others", "--exclude-standard").split()
    else:
        names = _git(root, "diff", "--name-only", base, head).split()
    files = {p for p in names if any(core.glob_match(p, g) for g in cfg["include"])
             and not any(core.glob_match(p, g) for g in cfg.get("exclude", []))}
    return Scope(root=root, files=sorted(files))


def source_at(root: Path, ref: str, rel: str) -> str | None:
    if ref == "WORKTREE":
        p = root / rel
        return p.read_text(encoding="utf-8") if p.is_file() else None
    r = subprocess.run(["git", "show", f"{ref}:{rel}"], cwd=root, capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def _files_at(root: Path, ref: str, cfg: dict) -> list[str]:
    if ref == "WORKTREE":
        return core.derive_scope(root, cfg["include"], cfg.get("exclude", [])).files
    names = _git(root, "ls-tree", "-r", "--name-only", ref).split()
    return [p for p in names if any(core.glob_match(p, g) for g in cfg["include"])
            and not any(core.glob_match(p, g) for g in cfg.get("exclude", []))]


def _decorator_name(d: ast.AST) -> str:
    return ast.unparse(d.func if isinstance(d, ast.Call) else d)


def guards_in(text: str) -> set[str]:
    tree, lines = ast.parse(text), text.splitlines()
    names = set()
    for n in ast.walk(tree):
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)):
            decorated = any(_decorator_name(d).split(".")[-1] == "safeguard" for d in n.decorator_list)
            marked = MARKER in lines[n.lineno - 1] or (n.lineno > 1 and MARKER in lines[n.lineno - 2])
            if decorated or marked:
                names.add(n.name)
    return names


def discover_guards(root: Path, refs: list[str], cfg: dict) -> tuple[set[str], int]:
    names, marked_lines = set(), 0
    for ref in refs:
        for rel in _files_at(root, ref, cfg):
            text = source_at(root, ref, rel)
            if text is not None:
                names |= guards_in(text)
                marked_lines += sum(MARKER in line for line in text.splitlines())
    return names, marked_lines


# ---- extraction -------------------------------------------------------------------------------

def _callee(call: ast.Call) -> str:
    f = call.func
    return f.attr if isinstance(f, ast.Attribute) else f.id if isinstance(f, ast.Name) else ""


def _is_neutered(h: ast.ExceptHandler) -> bool:
    return all(isinstance(s, (ast.Pass, ast.Continue)) or
               (isinstance(s, ast.Expr) and isinstance(s.value, ast.Constant)) for s in h.body)


def _broad(h: ast.ExceptHandler) -> bool:
    t = h.type
    names = [ast.unparse(e) for e in t.elts] if isinstance(t, ast.Tuple) else [ast.unparse(t)] if t else []
    return t is None or any(n.split(".")[-1] in _BROAD for n in names)


class _Extract(ast.NodeVisitor):
    def __init__(self, text: str, guards: set[str], canary_patterns: list[str]):
        self.lines = text.splitlines()
        self.marked = {i for i, line in enumerate(self.lines, start=1) if MARKER in line}
        self.guards, self.patterns = guards, canary_patterns
        self.items: dict[str, list[tuple[str, int]]] = defaultdict(list)
        self.func, self.in_try = ["<module>"], 0
        for i in sorted(self.marked):
            self.add("marker", self.lines[i - 1].strip(), i)

    def add(self, cat: str, src: str, line: int) -> None:
        self.items[cat].append((f"{self.func[-1]}|{src}", line))

    def is_guard_call(self, call: ast.Call) -> bool:
        name = _callee(call)
        return (name in self.guards or call.lineno in self.marked
                or any(core.glob_match(name, p) for p in self.patterns))

    def visit_FunctionDef(self, node) -> None:
        self.func.append(node.name)
        guard = node.name in self.guards
        a = node.args
        positional = [*a.posonlyargs, *a.args]
        defaulted = positional[len(positional) - len(a.defaults):] + \
            [k for k, d in zip(a.kwonlyargs, a.kw_defaults) if d is not None]
        for p in defaulted:
            if guard or p.arg == "contract":
                self.add("default", p.arg, node.lineno)
        for d in node.decorator_list:
            if _decorator_name(d).endswith(_SKIP_SUFFIXES):
                self.add("skip", ast.unparse(d), d.lineno)
        self.generic_visit(node)
        self.func.pop()

    visit_AsyncFunctionDef = visit_FunctionDef

    def visit_Try(self, node: ast.Try) -> None:
        self.in_try += 1
        for s in node.body:
            self.visit(s)
        self.in_try -= 1
        for part in (node.handlers, node.orelse, node.finalbody):
            for s in part:
                self.visit(s)

    def visit_ExceptHandler(self, node: ast.ExceptHandler) -> None:
        kind = ast.unparse(node.type) if node.type else "<bare>"
        if _broad(node):
            self.add("broad_handler", kind, node.lineno)
        if _is_neutered(node):
            self.add("neutered_handler", kind, node.lineno)
        self.generic_visit(node)

    def visit_Call(self, node: ast.Call) -> None:
        src, name = ast.unparse(node), _callee(node)
        if self.is_guard_call(node):
            self.add("guard_call", src, node.lineno)
            if self.in_try:
                self.add("wrapped", src, node.lineno)
        if name.startswith("assert"):
            self.add("assertion", src, node.lineno)
        if ast.unparse(node.func).endswith(_SKIP_SUFFIXES):
            self.add("skip", src, node.lineno)
        self.generic_visit(node)

    def visit_Assert(self, node: ast.Assert) -> None:
        self.add("assertion", ast.unparse(node), node.lineno)
        self.generic_visit(node)

    def visit_Expr(self, node: ast.Expr) -> None:
        if isinstance(node.value, ast.Call) and _callee(node.value) == "write_gate":
            self.add("discarded", ast.unparse(node), node.lineno)
        self.generic_visit(node)

    def visit_Assign(self, node: ast.Assign) -> None:
        to_underscore = any(isinstance(t, ast.Name) and t.id == "_" for t in node.targets)
        if to_underscore and isinstance(node.value, ast.Call) and _callee(node.value) == "write_gate":
            self.add("discarded", ast.unparse(node), node.lineno)
        self.generic_visit(node)

    def visit_If(self, node: ast.If) -> None:
        falsy = isinstance(node.test, ast.Constant) and not node.test.value
        if falsy:
            for c in (n for s in node.body for n in ast.walk(s) if isinstance(n, ast.Call) and self.is_guard_call(n)):
                self.add("unreachable", ast.unparse(c), c.lineno)
        self.generic_visit(node)

    def generic_visit(self, node: ast.AST) -> None:
        for field in ("body", "orelse", "finalbody"):
            stmts = getattr(node, field, None)
            if isinstance(stmts, list):
                self._after_exit(stmts)
        super().generic_visit(node)

    def _after_exit(self, stmts: list) -> None:
        exited = False
        for s in stmts:
            if exited:
                for c in (n for n in ast.walk(s) if isinstance(n, ast.Call) and self.is_guard_call(n)):
                    self.add("unreachable", ast.unparse(c), c.lineno)
            exited = exited or isinstance(s, _EXITS)


def extract(text: str | None, guards: set[str], patterns: list[str]) -> dict[str, list[tuple[str, int]]]:
    if text is None:
        return {}
    x = _Extract(text, guards, patterns)
    x.visit(ast.parse(text))
    return x.items


def _diff(rel: str, older: dict, newer: dict, cats: dict[str, str], lines: list[str], verb: str) -> list[Finding]:
    out = []
    for cat, rule in cats.items():
        have = Counter(k for k, _ in newer.get(cat, []))
        for key, line in older.get(cat, []):
            if have[key] > 0:
                have[key] -= 1
            else:
                text = lines[line - 1] if 0 < line <= len(lines) else ""
                out.append(Finding(rel, line, core.line_hash(text), rule, f"{verb}: {key.split('|', 1)[1]}"))
    return out


def compare(rel: str, base_text: str | None, head_text: str | None, guards: set[str], patterns: list[str]) -> list[Finding]:
    b, h = extract(base_text, guards, patterns), extract(head_text, guards, patterns)
    removed = _diff(rel, b, h, REMOVED, (base_text or "").splitlines(), "removed since base")
    added = _diff(rel, h, b, ADDED, (head_text or "").splitlines(), "new since base")
    return removed + added


# ---- run ---------------------------------------------------------------------------------------

def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    opts = opts or {}
    cfg = core.gate_section(config, GATE_ID)
    base, head = opts.get("base"), opts.get("head") or "HEAD"
    if not base:
        return core.not_run(GATE_ID, KIND, BLIND_SPOTS, "no --base given: a removal is only visible against a base")
    if len(scope) == 0:
        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)
    guards, marked = discover_guards(scope.root, [base, head], cfg)
    findings = []
    if not guards and not marked:
        findings.append(Finding("<scope>", 1, core.line_hash(""), "no_safeguards",
                                "no @safeguard function or '# safeguard' marker found in scope"))
    for rel in scope.files:
        findings += compare(rel, source_at(scope.root, base, rel), source_at(scope.root, head, rel),
                            guards, cfg["canary_call_patterns"])
    return GateResult(GATE_ID, Verdict.VIOLATED if findings else Verdict.SATISFIED, KIND,
                      findings=findings, scope_size=len(scope), blind_spots=BLIND_SPOTS,
                      details={"base": base, "head": head, "safeguards": sorted(guards), "marked_lines": marked})


def _commit_tree(repo: Path, tree: Path, message: str) -> str:
    for p in repo.iterdir():
        if p.name != ".git":
            shutil.rmtree(p) if p.is_dir() else p.unlink()
    shutil.copytree(tree, repo, dirs_exist_ok=True)
    ident = ["-c", "user.email=canary@invalid", "-c", "user.name=canary"]
    subprocess.run(["git", *ident, "add", "-A"], cwd=repo, check=True, capture_output=True)
    subprocess.run(["git", *ident, "commit", "-q", "--allow-empty", "-m", message], cwd=repo, check=True,
                   capture_output=True)
    return _git(repo, "rev-parse", "HEAD").strip()


def run_canary(canary_dir: Path) -> GateResult:
    """Canaries hold base/ and head/ trees, replayed as two real commits in a temporary repo."""
    config, opts = core.canary_setup(canary_dir)
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
        base = _commit_tree(repo, canary_dir / "base", "base")
        _commit_tree(repo, canary_dir / "head", "head")
        o = {**opts, "base": base, "head": "HEAD"}
        return run(config, diff_scope(repo, config, o), o)


def _args(ap) -> None:
    ap.add_argument("--base", help="git ref the change is compared against")
    ap.add_argument("--head", default="HEAD", help="git ref, or WORKTREE for uncommitted files")


def _scope(root: Path, config: dict, args) -> Scope:
    return diff_scope(root, config, vars(args))


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, run, __doc__, add_args=_args, scope_fn=_scope))
