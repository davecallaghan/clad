# LaTeX crib sheet

The gotchas at the bottom are the part worth rereading; the tables are
lookup.

## Operators

Every multi-letter operator lives in the paper's `macros.tex` and nowhere
else. Write `\Says_M(p \mid x)`, never `\operatorname{Says}_M(p \mid x)` and
never bare `Says_M`.

Why: bare `Says_M` sets as *S* × *a* × *y* × *s*, italic, with product
spacing. `\operatorname` gives upright type and correct spacing. Routing
everything through one definition makes a notation change a one-line edit.

Paper 1 defines 40 of them, grouped in `macros.tex` under:

| Group | Operators |
|---|---|
| Generation and disposition | `\Says` `\Conf` `\GenDisp` `\utter` `\TruthTracking` |
| Truth and evaluability | `\True` `\Truth` `\Resolved` `\Evaluable` |
| Evidence | `\Supports` `\EvidenceSupports` `\Authentic` `\Traceable` `\Current` `\TrainedOn` |
| Verification, authorization, warrant | `\Verified` `\Verification` `\Authorized` `\Warranted` `\AuthorizedReliance` |
| Model performance | `\LinguisticPlausibility` `\SemanticCoherence` `\BenchmarkAccuracy` `\Reliable` `\PredictiveSuccess` `\EpistemicSuccess` |
| Omniscience and finiteness | `\Omniscient` `\ConversationalOmniscience` `\Finite` `\CompleteWorldAccess` |
| Computation | `\GreaterComputationalPower` `\EfficientlyComputable` `\CorrectlyComputed` `\WorldDirectedSupport` `\AuthenticInputs` `\AdequateModel` `\ValidInterpretation` |
| Output-to-warrant pipeline | `\RawModelOutput` `\CandidateClaim` `\EvidenceEvaluation` |

Single-letter predicates need no macro: `K_A(p)`, `B_M(p)`, `M(x)`.

## Symbols

| Want | LaTeX | Notes |
|---|---|---|
| non-entailment | `\;\not\Rightarrow\;` | the house symbol for the whole program |
| implies | `\Rightarrow` | |
| iff | `\Leftrightarrow` | used for the warrant schemas |
| pipeline arrow | `\rightarrow` | as in output → candidate → warrant |
| not equivalent | `\not\equiv` | |
| ¬ | `\neg` | `\lnot` is the same glyph; paper 1 uses `\neg` |
| ∧ | `\land` | prefer over `+` for conjunction |
| ∀ | `\forall p \forall c\,` | the `\,` before the body is deliberate |
| conditional bar | `\mid` | correct spacing; a bare `\|` does not have it |
| ∈ ∪ ⊥ | `\in` `\cup` `\bot` | |
| ↦ | `\mapsto` | |
| ∼ | `\sim` | |
| ≈ ≥ ≠ | `\approx` `\ge` `\neq` | |
| Pr | `\Pr_M(...)` | `\Pr` is built in and already upright |
| 𝒟 | `\mathcal{D}` | script capitals only |
| θ ε | `\theta` `\varepsilon` | `\varepsilon` reads better for error bounds |
| Xᵀ | `X^{\top}` | `\top` beats a literal `T`, which reads as a variable |
| prose inside math | `\text{Bounded State}` | needs `amsmath` |

## Gotchas

**1. `[ ... ]` is not math.** Bare square brackets are literal characters.
Display math is `\[ ... \]` (unnumbered) or `\begin{equation} ... \end{equation}`
(numbered). Inline is `$ ... $`.

**2. Long displays need `multline*`.** A display that runs past the margin is
an *overfull box* — real text in the real margin, and arXiv will show it.
`\[ ... \]` cannot break across lines. Convert to:

```latex
\begin{multline*}
  \Warranted_{I,c}(p) \Leftrightarrow \Resolved(p,c)
  \land \Authentic(E) \land \Traceable(E)\\
  \land \Supports(E,p,c) \land \Verified(V,p,c)
  \land \Authorized(I,p,c).
\end{multline*}
```

The `\\` is where you choose the break. Four displays in paper 1 needed this.
`make check` counts the remaining boxes; the count should be zero.

**3. Braces are syntax.** `{` and `}` group. A literal brace is `\{` `\}`.

**4. `_` and `^` take exactly one token.** `x^10` renders as *x*¹0; write
`x^{10}`. Always brace subscripts longer than one character — `\Warranted_{I,c}`,
not `\Warranted_I,c`.

**5. Never hardcode a number.** `\label{sec:foo}` once, then `\ref{sec:foo}`,
or `\eqref{eq:foo}` for equations. Renumbering then takes care of itself, and
LaTeX Workshop autocompletes after `\ref{`.

**6. Blank line = new paragraph.** Single newlines are whitespace, so hard-wrap
source wherever you like. Two blank lines is the same as one.

**7. Escape these in prose:** `% & # _ $ { }` become `\% \& \# \_ \$ \{ \}`.
An unescaped `%` silently comments out the rest of the line.

**8. Quotes and dashes.** Opening double quote is two backticks, closing is two
apostrophes: ``` ``like this'' ```. Hyphen for compounds, `--` for ranges
(`pages 5--10`), `---` for an em dash.

**9. Citations.** `\citep{key}` → "(Author, Year)"; `\citet{key}` → "Author
(Year)" for use as a sentence subject. Keys come from `refs.bib`. Note the
bibliography is currently commented out in all three papers — see the README.

**10. `\newcommand` fails loudly on a name collision.** That is deliberate. If
a package ever claims `\True`, the build breaks instead of silently using
someone else's definition. Fix it by renaming yours, in `macros.tex`, once.

## Reading the build log

Two rules cover most of it. Fix the **first** error and rebuild — later errors
are usually cascade damage. And `! Missing $ inserted` almost always means a
math symbol appeared in prose, not that a `$` is missing where it says.

Stale `build/` output can also produce errors that survive a real fix; `make
clean` when something makes no sense.

One trap specific to this repo: **zsh's `echo` interprets backslash escapes.**
`echo '\begin{document}'` emits a literal backspace byte, and the resulting
LaTeX error (`Unicode character ^^H (U+0008)`) points nowhere near the cause.
Use `printf '%s\n'` or a quoted heredoc when scripting LaTeX.
