#!/usr/bin/env python3
"""Markdown -> LaTeX for the CLAD governance corpus.

Design decisions, recorded because they are choices rather than defaults:

* Fenced blocks become `verbatim`. The source blocks carry meaningful ASCII
  alignment (aligned axiom clauses, indented proof steps) that math mode would
  destroy, and there are 106 of them in this chapter alone. Notation
  substitutions are therefore applied as *literal text* inside blocks and as
  *macros* in surrounding prose. Individual blocks can be promoted to real math
  later where it pays.
* Heading depth maps to the book class: `#` -> chapter, `##` -> section,
  `###` -> subsection.
* The source's section numbers are stripped. They are inconsistent in the
  original (section 3 contains subsections numbered 2.x, section 4 contains 3.x)
  because the chapter was stitched from separate white papers. LaTeX numbers
  them correctly.
"""
import re, sys

# Notation substitutions, per NOTATION-MAP.md. Order matters: longest first.
PROSE = [
    # The notation table replaces four CLAD symbols with operator macros so the
    # two halves of the book share one vocabulary (see book/NOTATION-MAP.md).
    # None of this ran until now: the map was defined and never called, so prose
    # carried raw gamma(S), Phi(g) and P_meta(...) in 35 places while the
    # monospace blocks already showed the unified names. Two entries were also
    # broken -- r'$\\Obl(\\1)$' emits a literal 1, not the capture group.
    #
    # O(...) and F(...) are deliberately left alone: their arguments are
    # snake_case identifiers, which do not survive math mode intact.
    (r'\bP_meta\(([^)]*)\)',   r'$\\Permitted$(\1)'),
    (r'γ\(S_([a-z]+)\)',       r'$\\gov(\\sigma_{\\mathrm{\1}})$'),
    (r'γ\(S\)',                r'$\\gov(\\sigma)$'),
    (r'Φ\(g_([A-Za-z]+)\)',    r'$\\guarantee(g_{\\mathrm{\1}})$'),
    (r'Φ\(g([0-9]*)\)',        r'$\\guarantee(g\1)$'),
    (r'A_g\(i,\s?t\)',         r'$\\Audit_g(i,t)$'),
]

# Inside verbatim: literal names, no macros. Unicode operators become ASCII.
#
# Decision recorded: pdflatex cannot typeset Unicode inside `verbatim`, and
# switching to lualatex costs a font-cache build measured in minutes. Prose gets
# real math symbols via _esc_plain; formal blocks get ASCII operators, which is
# an ordinary convention in formal-methods texts and preserves the source's
# column alignment exactly. Promoting individual blocks to real math environments
# is future work where it pays.
UNI = [
    ('∀', 'forall '), ('∃', 'exists '), ('∈', ' in '), ('∉', ' notin '),
    ('⊆', ' subseteq '), ('⊎', ' uplus '), ('∪', ' U '), ('∩', ' ^ '),
    ('⊨', ' |= '), ('→', ' -> '), ('↔', ' <-> '), ('⇒', ' => '),
    ('≻', ' > '), ('≽', ' >= '), ('≺', ' < '), ('≼', ' <= '),
    ('≤', ' <= '), ('≥', ' >= '), ('≠', ' != '), ('≡', ' == '),
    ('∧', ' and '), ('∨', ' or '), ('¬', 'not '), ('⊕', ' (+) '),
    ('∎', 'QED'), ('γ', 'gamma'), ('Φ', 'Phi'), ('φ', 'phi'),
    ('θ', 'theta'), ('Θ', 'Theta'), ('σ', 'sigma'), ('Σ', 'Sigma'),
    ('τ', 'tau'), ('ρ', 'rho'), ('𝒫', 'P'), ('ℕ', 'N'), ('ℝ', 'R'),
    ('—', '--'), ('–', '-'), ('·', '.'), ('×', ' x '), ('′', "'"),
    ('“', '"'), ('”', '"'), ('‘', "'"), ('’', "'"), ('…', '...'),
    ('₀','0'), ('₁','1'), ('₂','2'), ('₃','3'), ('₄','4'), ('₅','5'),
    ('ᵢ','i'), ('ⱼ','j'), ('ₖ','k'), ('ₙ','n'), ('ₘ','m'), ('ₚ','p'),
    ('§','S'), ('∅',' {} '), ('←',' <- '), ('⋃',' Union '), ('⊇',' supseteq '),
    ('⊥',' bot '), ('⊤',' top '), ('∥',' || '), ('≈',' ~= '), ('Δ','Delta'), ('‑','-'), ('ψ','psi'), ('⇔',' <=> '),
]

VERB = [
    ('P_meta(', 'P_meta('),
    ('γ(S', 'gov(σ'), ('Φ(g', 'guar(g'), ('S_prompt', 'σ_prompt'),
    ('S_input', 'σ_input'), ('S_config', 'σ_config'), ('S_output', 'σ_output'),
    ('S_delivery', 'σ_delivery'), ('A_g(', 'Audit_g('), ('A_EPG', 'Audit_EPG'),
    ('A_ROC', 'Audit_ROC'), ('A_MDR', 'Audit_MDR'),
]

def _tighten(s):
    """Collapse the double spaces the ASCII operator map introduces, keeping
    runs of three or more, which are column alignment."""
    return re.sub(r'(?<! ) {2}(?! )', ' ', s)

def prose_macros(t):
    """Unify CLAD's symbols with the book's operator macros."""
    for pat, rep in PROSE:
        t = re.sub(pat, rep, t)
    return t


def _mono(s):
    """Escape for a monospace display. Typewriter fonts have no --- ligature, so
    the em dash that _esc_plain turns into --- came out as an en dash followed by
    a hyphen; ask for the glyph directly instead."""
    return _esc_plain(s).replace('---', r'\textemdash{}')

def esc(t):
    """Escape LaTeX specials in prose. Leaves $...$ and \\cmd alone."""
    out, i = [], 0
    for m in re.finditer(r'(\$[^$]*\$|\\[a-zA-Z]+(?:\{[^}]*\})*)', t):
        seg = t[i:m.start()]
        out.append(_esc_plain(seg)); out.append(m.group(0)); i = m.end()
    out.append(_esc_plain(t[i:]))
    # An underscore in prose is always part of an identifier, never English, so a
    # zero-width break there is safe -- and necessary, because
    # F(operational_commands_without_human_confirmation) has no other break point
    # and ran 190pt off the margin.
    return ''.join(out)

def _esc_plain(s):
    for a, b in [('\\', r'\textbackslash{}'), ('&', r'\&'), ('%', r'\%'),
                 ('#', r'\#'), ('_', r'\_'), ('{', r'\{'), ('}', r'\}'),
                 ('~', r'\textasciitilde{}'), ('^', r'\textasciicircum{}')]:
        s = s.replace(a, b)
    s = s.replace('→', r'$\to$').replace('⊨', r'$\vDash$').replace('≻', r'$\succ$')
    s = s.replace('∀', r'$\forall$').replace('∃', r'$\exists$').replace('∈', r'$\in$')
    s = s.replace('⊆', r'$\subseteq$').replace('∪', r'$\cup$').replace('∩', r'$\cap$')
    s = s.replace('≤', r'$\leq$').replace('≥', r'$\geq$').replace('≠', r'$\neq$')
    s = s.replace('γ', r'$\gamma$').replace('Φ', r'$\Phi$').replace('φ', r'$\phi$')
    s = s.replace('θ', r'$\theta$').replace('Θ', r'$\Theta$').replace('σ', r'$\sigma$')
    s = s.replace('Σ', r'$\Sigma$').replace('⊕', r'$\oplus$').replace('∎', r'$\qed$')
    s = s.replace('—', '---').replace('–', '--').replace('‑', '-')
    for a, b in [('§', r'\S{}'), ('∅', r'$\emptyset$'), ('←', r'$\leftarrow$'),
                 ('⋃', r'$\bigcup$'), ('⊇', r'$\supseteq$'), ('⊥', r'$\bot$'), ('⊤', r'$\top$'),
                 ('∥', r'$\parallel$'), ('≈', r'$\approx$'), ('Δ', r'$\Delta$'),
                 ('∧', r'$\wedge$'), ('∨', r'$\vee$'), ('¬', r'$\neg$'),
                 ('⊎', r'$\uplus$'), ('≡', r'$\equiv$'), ('↔', r'$\leftrightarrow$'),
                 ('⇒', r'$\Rightarrow$'), ('≽', r'$\succeq$'), ('≺', r'$\prec$'),
                 ('≼', r'$\preceq$'), ('∉', r'$\notin$'), ('·', r'$\cdot$'),
                 ('×', r'$\times$'), ('′', r"$'$"), ('…', r'\ldots{}'),
                 ('τ', r'$\tau$'), ('ρ', r'$\rho$'), ('𝒫', r'$\mathcal{P}$'), ('𝓜', r'$\mathcal{M}$'),
                 ('ℕ', r'$\mathbb{N}$'), ('ℝ', r'$\mathbb{R}$'),
                 ('ψ', r'$\psi$'), ('⇔', r'$\Leftrightarrow$'), ('α', r'$\alpha$'),
                 ('β', r'$\beta$'), ('λ', r'$\lambda$'), ('μ', r'$\mu$'),
                 ('ε', r'$\varepsilon$'), ('δ', r'$\delta$'), ('Ω', r'$\Omega$'),
                 ('Ψ', r'$\Psi$'), ('Λ', r'$\Lambda$'), ('∑', r'$\sum$'),
                 ('₀','$_0$'), ('₁','$_1$'), ('₂','$_2$'), ('₃','$_3$'),
                 ('ᵢ','$_i$'), ('ⱼ','$_j$'), ('ₖ','$_k$'), ('ₙ','$_n$'),
                 ('“', '``'), ('”', "''"), ('‘', '`'), ('’', "'")]:
        s = s.replace(a, b)
    s = s.replace('"', "``", 1) if s.count('"') >= 2 else s
    return s

CLAD_CH = {'1': 'framework', '2': 'prompt', '3': 'controls', '4': 'monitoring'}


def clad_chapters(t):
    """Rewrite the CLAD corpus's own chapter numbers as book references.

    A section number attached to such a reference belongs to THAT chapter, not to
    the file being converted, so it is emitted as literal text: mdr's
    "(Chapter 2, section 4)" was resolving against mdr's own section 4.
    """
    def sub(m):
        tail = m.group(2) or ''
        if tail:
            tail = ', \\S' + tail.split('§')[-1].strip()
        return 'Chapter \\ref{ch:%s}%s' % (CLAD_CH[m.group(1)], tail)
    # Plural first, or the singular pattern never sees "Chapters 2 and 3" -- it
    # requires whitespace directly after "Chapter" and finds an "s".
    def sub_pair(m):
        return 'Chapters \\ref{ch:%s} and \\ref{ch:%s}' % (
            CLAD_CH[m.group(1)], CLAD_CH[m.group(2)])
    # Appendix subsections carry their own numbers in the source; they are
    # labelled at emission, so a reference to one resolves rather than drifting
    # when a subsection is inserted ahead of it.
    t = re.sub(r'(?:Appendix\s+)?(A\.\d+(?:\.\d+)?)\b',
               lambda m: 'Section~\\ref{sec:formal-model-'
                         + m.group(1).replace('.', '-') + '}', t)
    t = re.sub(r'Chapters\s+([1-4])\s+and\s+([1-4])\b', sub_pair, t)
    return re.sub(r'Chapter\s+([1-4])\b(\s*,\s*§\s*[\d.]+)?', sub, t)

def xref(t, label, known=None):
    """Turn a bare 'SS N' into a \ref, but ONLY when this file actually labels a
    section N. Two things otherwise get swept up: regulatory citations, where
    'SS164.502(a)' is a CFR paragraph and not a cross-reference at all, and
    cross-document pointers, where an appendix's 'SS3' means section 3 of a
    chapter and carries that chapter's label, not this one's. Both are left as
    literal section signs -- a wrong \ref is worse than an unlinked pointer."""
    def sub(m):
        n = m.group(1)
        if known is not None and n not in known:
            return '\\S' + m.group(0)[1:].lstrip()
        return f'Section~\\ref{{sec:{label}-{n}}}'
    return re.sub(r'§\s*(\d+)(?![\d.]*\.\d)\b', sub, clad_chapters(t))


def labelled_sections(src):
    """The section numbers this file will emit labels for -- the same '## N. Title'
    shape the converter keys its \label emission off."""
    found = set()
    for ln in src.split('\n'):
        m = re.match(r'^##\s+(?:Chapter\s+\d+\s*[—-]\s*)?(\d+)[.\s]', ln)
        if m:
            found.add(m.group(1))
    return found

def _breakable(t):
    """Insert zero-width break opportunities in monospaced identifiers."""
    t = t.replace(r'\_', r'\_\allowbreak{}')
    t = t.replace('(', r'(\allowbreak{}')
    return t

def linebreaks(t):
    """Add zero-width break points to long identifiers, as the LAST step -- after
    all escaping, or inline()'s code-span escaping mangles the \allowbreak into
    visible text. An underscore in prose is always part of an identifier, so a
    break there is safe; without one, F(forward_looking_statements_without_...)
    has no break point and runs off the margin."""
    return t.replace(r'\_', r'\_\allowbreak{}')


def inline(t):
    t = re.sub(r'\*\*(.+?)\*\*', r'\\textbf{\1}', t)
    t = re.sub(r'(?<!\*)\*([^*]+?)\*(?!\*)', r'\\emph{\1}', t)
    t = re.sub(r'`([^`]+?)`', lambda m: r'\texttt{' + m.group(1) + '}', t)
    t = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', t)
    return t

# Formal statements in the CLAD corpus are fenced blocks headed
# "THEOREM 3b (Ghost Detection):". Rendered as verbatim they printed as
# monospaced slabs while the same kinds of statement in the rest of the book used
# amsthm, which is the formatting inconsistency this resolves.
STMT_ENV = {
    'THEOREM': 'cthm', 'LEMMA': 'clem', 'COROLLARY': 'ccor',
    'PROPOSITION': 'cprop', 'DEFINITION': 'cdef', 'PROPERTY': 'cprp',
    'REQUIREMENT': 'creq', 'ADDITIONAL REQUIREMENT': 'creq',
    'PRINCIPLE': 'cprn', 'INVARIANT': 'cinv',
}
STMT_RE = re.compile(
    r'^(THEOREM|LEMMA|COROLLARY|PROPOSITION|DEFINITION|PROPERTY|'
    r'ADDITIONAL REQUIREMENT|REQUIREMENT|PRINCIPLE|INVARIANT)'
    r'(\s+[0-9][0-9a-z.]*)?\s*(\(.*\))?\s*:\s*$')


def _dedent(block):
    """Remove the indent common to every non-blank line."""
    ind = [len(l) - len(l.lstrip(' ')) for l in block if l.strip()]
    n = min(ind) if ind else 0
    return [l[n:] if l.strip() else '' for l in block]


SUBHEAD_RE = re.compile(
    r'^(CAVEAT|PROTOCOL|IMPORTANT DISTINCTION|EVALUATION RECORD|PROCESS RECORD|'
    r'MODELING SIMPLIFICATION|NOTE|SCOPE)\s*(\([^)]*\))?\s*:\s*(.*)$')

# Lines that open with one of these are notation, not a sentence.
_OPS = '\u2200\u2203\u2208\u2209\u2286\u2282\u2287\u222a\u2229\u228e\u22a8\u2192\u21d2\u21d4\u22c3\u227b\u2295\u2264\u2265\u2260\u2261'
_MATHY = '∀∃⋃⊆⊂⊇∈∪∩⊕⊎≻⊕'


def _is_display(line):
    """Decide whether a line inside a formal statement is aligned notation or a
    sentence. Indentation alone was the wrong signal: these blocks indent
    continuation lines of ordinary prose as well as genuine displays."""
    t = line.strip()
    if not t:
        return False
    if '   ' in t:                       # column alignment
        return True
    if t[0] in _MATHY:
        return True
    if re.match(r'^[A-Za-z_][A-Za-z0-9_\']*\s*(:=|=|:)\s*\S', t) and not t.endswith('.'):
        return True
    words = [w for w in re.findall(r"[A-Za-z][A-Za-z'\-]{2,}", t)]
    return len(words) < 3               # mostly symbols


def _fold(block):
    """Fold wrapped prose back into logical lines.

    Two signals, because indentation alone is not enough: these blocks wrap prose
    at the same indent as often as they indent a continuation. A line continues
    the one before it when it is more deeply indented, or when the previous line
    ran close to the wrap width without ending a sentence. Length is what
    separates a wrapped sentence from two parallel conjuncts -- "Phi(g_1) holds
    regardless of whether g_2 is deployed" is short and complete, where "A Global
    Interaction Log records the existence of every AI interaction" is 69
    characters and plainly unfinished.
    """
    WRAP = 58
    out = []
    for l in block:
        if not l.strip():
            continue
        ind = len(l) - len(l.lstrip(' '))
        t = l.strip()
        cont = False
        if out and not t.startswith('- '):
            pind, ptxt = out[-1]
            if ind > pind:
                cont = True
            elif (ind == pind and len(ptxt) >= WRAP
                  and not re.search(r'[.;:]$', ptxt) and not _is_display(t)):
                cont = True
        if cont:
            out[-1] = (out[-1][0], out[-1][1] + ' ' + t)
        else:
            out.append((ind, t))
    return out


def _emit_prose(folded, out):
    """Prose lines as prose, dash lists as itemize -- the environment the rest of
    the book uses for the same thing."""
    buf = []
    def flush(out=out, buf=buf):
        if buf:
            out.append(r'\begin{itemize}')
            out += [r'  \item ' + linebreaks(inline(esc(prose_macros(x)))) for x in buf]
            out.append(r'\end{itemize}')
            buf.clear()
    for _, t in folded:
        if t.startswith('- '):
            buf.append(t[2:].strip())
        else:
            flush()
            out.append(linebreaks(inline(esc(prose_macros(t)))))
    flush()
    out.append('')


def _stmt_part(block, out):
    """Render one part of a statement: prose as prose, aligned notation as an
    indented monospace note. Alignment is load-bearing in these blocks (tier
    lists, pipeline stages), so it is preserved rather than reflowed."""
    block = [l.replace('∎', '').rstrip() for l in block]   # amsthm draws the QED box
    paras, cur = [], []
    for l in block:
        if l.strip():
            cur.append(l)
        elif cur:
            paras.append(cur); cur = []
    if cur:
        paras.append(cur)
    for p in paras:
        p = _dedent(p)
        m = SUBHEAD_RE.match(p[0])
        if m:
            label = m.group(1).title() + (' ' + m.group(2) if m.group(2) else '')
            out.append(r'\smallskip\noindent\textbf{%s.}' % esc(label))
            rest = ([m.group(3)] if m.group(3).strip() else []) + p[1:]
            if any(x.strip() for x in rest):
                _stmt_part(rest, out)
            continue
        folded = _fold(p)
        body = [t for _, t in folded if not t.startswith('- ')]
        # Several logical lines, most not ending a sentence, are parallel
        # conjuncts or aligned notation rather than flowing prose; set as prose
        # they would run together into one line.
        unpunct = sum(1 for t in body if not re.search(r'[.;:]$', t))
        listish = len(body) > 1 and unpunct * 2 > len(body)
        if listish or any(_is_display(t) for t in body):
            out.append(r'\begin{cladnote}')
            for l in p:
                w = l
                for a, b in VERB: w = w.replace(a, b)
                lead = len(w) - len(w.lstrip(' '))
                # cladnote is ordinary LaTeX text, where runs of spaces collapse
                # to one. These blocks align colons and columns, so any run of
                # two or more becomes hard spaces; single spaces stay breakable
                # so a long line can still wrap.
                txt = re.sub(r' {2,}', lambda m: '~' * len(m.group(0)),
                             _mono(w[lead:]))
                out.append('~' * lead + txt + r'\\')
            out.append(r'\end{cladnote}')
        else:
            _emit_prose(folded, out)


def formal_statement(body, out):
    """Emit the CLAD formal statements in one fenced block as amsthm
    environments, or return False if the block is not a statement.

    Ten of these blocks hold more than one statement -- a lemma and its
    corollary, a theorem and the property that qualifies it -- so the block is
    split at every header, not just the first.
    """
    if not body or not STMT_RE.match(body[0]):
        return False
    heads = [k for k, l in enumerate(body) if STMT_RE.match(l)]
    if len(heads) > 1:
        for a, b in zip(heads, heads[1:] + [len(body)]):
            _one_statement(body[a:b], out)
        return True
    return _one_statement(body, out)


def _one_statement(body, out):
    m = STMT_RE.match(body[0])
    if not m:
        return False
    env = STMT_ENV[m.group(1)]
    desig = ((m.group(2) or '').strip() + ' ' + (m.group(3) or '').strip()).strip()
    rest = body[1:]
    # A trailing "Proof:" becomes a real proof environment with its QED box.
    cut = next((k for k, l in enumerate(rest)
                if re.match(r'^\s*Proof\s*:\s*$', l)), None)
    stmt = rest[:cut] if cut is not None else rest
    out.append(r'\begin{%s}%s' % (env, '[%s]' % esc(desig) if desig else ''))
    _stmt_part(stmt, out)
    out.append(r'\end{%s}' % env)
    out.append('')
    if cut is not None:
        proof = rest[cut + 1:]
        if any(l.strip() for l in proof):
            out.append(r'\begin{proof}')
            _stmt_part(proof, out)
            out.append(r'\end{proof}')
            out.append('')
    return True

def convert(src, chapter_label):
    known = labelled_sections(src)
    lines = src.split('\n')
    out, i, in_fence, fence_group = [], 0, False, []
    axiom_end = None
    while i < len(lines):
        ln = lines[i]
        if ln.startswith('```'):
            if not in_fence:
                # verbatim cannot wrap, so the block has to be sized to its
                # longest line up front. Mermaid fences are diagram specs, not
                # text a reader wants: they are dropped with a marker rather
                # than dumped as source (one ran 619pt off the page).
                if ln[3:].strip().lower() == 'mermaid':
                    j = i + 1
                    while j < len(lines) and not lines[j].startswith('```'):
                        j += 1
                    out += [r'% DIAGRAM (mermaid source dropped) -- render as a'
                            r' figure by hand; see fig:mdr-flows for the pattern',
                            '']
                    i = j + 1; continue
                # A fenced block headed "THEOREM ... :" is a formal statement,
                # not code: render it with the same amsthm machinery the rest of
                # the book uses rather than as a monospaced slab.
                j = i + 1
                blk = []
                while j < len(lines) and not lines[j].startswith('```'):
                    blk.append(lines[j]); j += 1
                if formal_statement(blk, out):
                    i = j + 1; continue

                # A fenced block that carries real notation is display material,
                # not code: set it the same way a statement's notation is set, so
                # the operators are symbols rather than the words "forall" and
                # "subseteq". Code-like blocks (threat lists, record layouts,
                # pseudocode) stay in verbatim, where ASCII is right.
                # Every fenced block that is not code is display notation, and
                # they are all set the same way. Only the widest need a
                # smaller font, which cladnote's \small can be overridden with.
                if blk:
                    widest = max((len(x) for x in blk), default=0)
                    sz = ('' if widest <= 84 else
                          r'\footnotesize' if widest <= 96 else
                          r'\scriptsize' if widest <= 112 else r'\tiny')
                    out.append(r'\begin{cladnote}' + sz)
                    # Lines within a run are joined by \\; a blank line becomes a
                    # paragraph break. A \\ on an empty line is the LaTeX error
                    # "There's no line here to end".
                    run = []
                    def _flush(run=run, out=out):
                        if run:
                            out.extend(x + r'\\' for x in run[:-1])
                            out.append(run[-1])
                            run.clear()
                    for l in blk + ['']:
                        if not l.strip():
                            if run:
                                _flush(); out.append(r'\par\smallskip')
                            continue
                        w = l
                        for a, b in VERB: w = w.replace(a, b)
                        lead = len(w) - len(w.lstrip(' '))
                        txt = re.sub(r' {2,}', lambda m: '~' * len(m.group(0)),
                                     _mono(w[lead:]))
                        run.append('~' * lead + txt)
                    while out and out[-1] == r'\par\smallskip':
                        out.pop()
                    out.append(r'\end{cladnote}')
                    out.append('')
                    i = j + 1; continue

                widest, j = 0, i + 1
                while j < len(lines) and not lines[j].startswith('```'):
                    w = lines[j]
                    for a, b in VERB: w = w.replace(a, b)
                    w = _tighten(w)
                    widest = max(widest, len(w.rstrip())); j += 1
                vsize = (''                 if widest <= 84  else
                         r'\footnotesize'   if widest <= 96  else
                         r'\scriptsize'     if widest <= 112 else r'\tiny')
                if vsize:
                    out.append(r'\begingroup' + vsize)
                    fence_group.append(True)
                else:
                    fence_group.append(False)
                out.append(r'\begin{verbatim}'); in_fence = True
            else:
                out.append(r'\end{verbatim}')
                if fence_group.pop():
                    out.append(r'\endgroup')
                in_fence = False
            i += 1; continue
        if in_fence:
            for a, b in VERB: ln = ln.replace(a, b)
            for a, b in UNI:  ln = ln.replace(a, b)
            out.append(_tighten(ln)); i += 1; continue

        # Close an open Axiom statement before the discussion that follows it.
        if axiom_end is not None and i >= axiom_end:
            out.append(r'\end{caxiom}'); out.append('')
            axiom_end = None

        am = re.match(r'^###\s+Axiom\s+(\d+)\s*(\([^)]*\))?\s*$', ln)
        if am:
            # The statement runs to the first bold-led paragraph (or the next
            # heading); the discussion after it stays ordinary prose rather than
            # being swallowed into an environment spanning pages.
            axiom_end = next((k for k in range(i + 1, len(lines))
                              if lines[k].startswith('**') or lines[k].startswith('#')),
                             len(lines))
            desig = (am.group(1) + ' ' + (am.group(2) or '')).strip()
            out.append(r'\begin{caxiom}[%s]' % esc(desig))
            i += 1; continue

        m = re.match(r'^(#{1,4})\s+(.*)$', ln)
        if m:
            depth, title = len(m.group(1)), m.group(2).strip()
            sn = re.match(r'^(\d+)\.\s', title)
            secnum = sn.group(1) if sn else None
            # Strip the source's own numbering: LaTeX supplies "Appendix D",
            # so a title of "Appendix C -- Templates" prints as "Appendix D --
            # Appendix C -- Templates" and contradicts its own letter.
            apnum = re.match(r'^([A-Z]\.\d+(?:\.\d+)?)\s+', title)
            title = re.sub(r'^(Chapter\s+\d+\s*[—–-]\s*'
                           r'|Appendix\s+[A-Z]\s*[—–-]\s*'
                           r'|[A-Z]\.\d+(?:\.\d+)?\s+'
                           r'|\d+(\.\d+)*\.?\s+)', '', title)
            # Headings need escaping too, and it must be light: full esc() would
            # mangle a title, but an unescaped _ breaks both the heading and its
            # ToC entry ("EPG as Component g_EPG" did exactly that).
            for a, b in [('_', r'\_'), ('&', r'\&'), ('%', r'\%'), ('#', r'\#')]:
                title = title.replace(a, b)
            # Em dashes and other Unicode reach titles too: 'Appendix A - Formal
            # Model' had a raw em dash survive because the prose map never ran here.
            for a, b in UNI:
                if not b.startswith('$'):
                    title = title.replace(a, b)
            title = title.replace('\u2014', '---').replace('\u2013', '--')
            title = xref(inline(title), chapter_label, known)
            cmd = {1: 'chapter', 2: 'section', 3: 'subsection', 4: 'subsubsection'}[depth]
            out.append(f'\\{cmd}{{{title}}}')
            if apnum:
                out.append('\\label{sec:%s-%s}' % (chapter_label,
                                                    apnum.group(1).replace('.', '-')))
            if depth == 1:
                out.append(f'\\label{{ch:{chapter_label}}}')
            elif depth == 2 and secnum:
                # Label top-level sections by their SOURCE number so that the
                # corpus's own cross-references (which are written as "section N")
                # can be resolved to real LaTeX refs rather than left as stale text.
                out.append(f'\\label{{sec:{chapter_label}-{secnum}}}')
            out.append('')
            i += 1; continue

        # Markdown pipe table.
        if ln.lstrip().startswith('|') and i + 1 < len(lines) and \
           re.match(r'^\s*\|[\s:|-]+\|\s*$', lines[i+1]):
            def cells(row):
                return [c.strip() for c in row.strip().strip('|').split('|')]
            hdr = cells(ln)
            i += 2
            body = []
            while i < len(lines) and lines[i].lstrip().startswith('|'):
                body.append(cells(lines[i])); i += 1
            n = len(hdr)
            # Size each column to its widest cell, floored so a short header is
            # not squeezed, then normalise to the text block. Wide tables step
            # down in font size rather than in column width -- the regulatory
            # crosswalk is 8 columns and an equal split would be unreadable.
            widths = []
            for k in range(n):
                longest = max([len(hdr[k])] +
                              [len(r[k]) for r in body if k < len(r)])
                widths.append(max(longest, 6))
            avail = 15.4 - 0.35 * n          # leave room for intercolumn space
            cm = [max(1.15, avail * w / sum(widths)) for w in widths]
            if sum(cm) > avail:              # floors overflowed: rescale
                cm = [c * avail / sum(cm) for c in cm]
            size = (r'\small' if n <= 4 else
                    r'\footnotesize' if n <= 6 else r'\scriptsize')
            spec = ''.join(r'>{\raggedright\arraybackslash}p{%.2fcm}' % c for c in cm)
            def fmt(row):
                row = (row + [''] * n)[:n]
                return ' & '.join(linebreaks(xref(inline(esc(c)), chapter_label, known))
                  for c in row) + r' \\'
            # A long reference table must not float: the assumption table
            # drifted past its own introduction, so the reader met "they are
            # collected here" two paragraphs before the table. longtable sets it
            # in place and breaks it across pages.
            if len(body) > 12:
                out += [size, r'\begin{longtable}{@{}' + spec + r'@{}}',
                        r'  \toprule', '  ' + fmt(hdr), r'  \midrule',
                        r'\endfirsthead', r'  \toprule', '  ' + fmt(hdr),
                        r'  \midrule', r'\endhead',
                        r'  \bottomrule', r'\endlastfoot']
                out += ['  ' + fmt(r) for r in body]
                out += [r'\end{longtable}', r'\normalsize', '']
            else:
                out += [r'\begin{table}[ht]', r'  \centering' + size,
                        r'  \begin{tabular}{@{}' + spec + r'@{}}', r'  \toprule',
                        '  ' + fmt(hdr), r'  \midrule']
                out += ['  ' + fmt(r) for r in body]
                out += [r'  \bottomrule', r'  \end{tabular}', r'\end{table}', '']
            continue

        # 4-space indented display block (wp1's convention for formal
        # notation). Guard: must not be immediately preceded by a list item, or
        # it is a continuation line rather than a display block.
        if re.match(r'^    \S', ln) and not (out and out[-1].strip().startswith(r'\item')):
            # Same treatment as a statement's notation: cladnote with real math
            # symbols. This path applied the ASCII map, which is why
            # "forall c : not emergency(c)" appeared a few pages after the very
            # same operators had been set as symbols.
            out.append(r'\begin{cladnote}')
            run = []
            def _flush(run=run, out=out):
                if run:
                    out.extend(x + r'\\' for x in run[:-1])
                    out.append(run[-1])
                    run.clear()
            while i < len(lines) and (re.match(r'^    ', lines[i]) or not lines[i].strip()):
                if lines[i].strip():
                    body = lines[i][4:]
                    for a, b in VERB: body = body.replace(a, b)
                    txt = _mono(body)
                    lead = len(txt) - len(txt.lstrip(' '))
                    # Runs of two or more spaces are column alignment and must be
                    # hard; single spaces stay breakable so a long line can wrap
                    # rather than run off the page.
                    run.append('~' * lead + re.sub(
                        r' {2,}', lambda m: '~' * len(m.group(0)), txt[lead:]))
                elif i + 1 < len(lines) and re.match(r'^    \S', lines[i+1]):
                    _flush(); out.append(r'\par\smallskip')
                else:
                    break
                i += 1
            _flush()
            while out and out[-1] == r'\par\smallskip':
                out.pop()
            out.append(r'\end{cladnote}'); out.append(''); continue

        if re.match(r'^\s*[-*]\s+', ln):
            out.append(r'\begin{itemize}')
            # A wrapped list item is one item, not two. Emitting \item per source
            # line turned four learning objectives into eight, half of them
            # fragments ("violates them;").
            items = []
            while i < len(lines) and (re.match(r'^\s*[-*]\s+', lines[i]) or
                                      (lines[i].startswith('  ') and lines[i].strip())):
                if re.match(r'^\s*[-*]\s+', lines[i]):
                    items.append(re.sub(r'^\s*[-*]\s+', '', lines[i]).strip())
                elif items:
                    items[-1] += ' ' + lines[i].strip()
                else:
                    items.append(lines[i].strip())
                i += 1
            for it in items:
                out.append(r'  \item ' + linebreaks(xref(inline(esc(prose_macros(it))), chapter_label, known)))
            out.append(r'\end{itemize}'); out.append(''); continue

        if ln.strip() == '---':
            out.append(''); i += 1; continue
        if not ln.strip():
            out.append(''); i += 1; continue

        out.append(linebreaks(xref(inline(esc(prose_macros(ln))), chapter_label, known))); i += 1
    return '\n'.join(out)

if __name__ == '__main__':
    src = open(sys.argv[1]).read()
    print(convert(src, sys.argv[2]))
