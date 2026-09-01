# Three-paper research program

Epistemology, governance, and verification of AI-mediated decision systems in
regulated enterprises.

| Directory | Paper | State |
|---|---|---|
| [paper1-grounding/](paper1-grounding/) | **Accuracy Is Not Auditable: Grounding as the Measurable Property of Language Model Assertion** | full draft, 36 pp — **this is the paper 1 submission** |
| [paper1-subdoxastic/](paper1-subdoxastic/) | Assertion Without Belief: Large Language Models as Sub-Doxastic Systems | full draft, 30 pp — formal reference, **shelved, not for submission** |
| [paper2-institutional-warrant/](paper2-institutional-warrant/) | Institutional Warrant and Auditable AI | scaffold only |
| [paper3-verified-computation/](paper3-verified-computation/) | Verified Computation and Computational Warrant | scaffold only |

The intended progression:

    raw model output -> candidate proposition -> evidence-grounded evaluation
      -> verification -> authorized institutional reliance

and for complex computation:

    computational correctness != empirical truth != institutional warrant

Each paper must state its own scope, assumptions, definitions, and thesis
well enough to stand alone.

## Two versions of paper 1

`paper1-grounding/` and `paper1-subdoxastic/` argue overlapping theses for
different audiences, and **only one of them is submitted.**

- **`paper1-grounding/` is the submission.** Engineering-facing. The formalism is
  retained so that claims are checkable, but the philosophical apparatus and
  citations are not: the modal axioms appear as *invariants a reliable assertion
  system would satisfy*, and hyperintensionality appears as *there is no canonical
  form* — the system is keyed on the surface string, like a badly keyed cache. Its
  spine is failure taxonomy, then why the failures are structural, then why
  non-repeatability makes accuracy uncertifiable, then grounding as the property
  that can be gated on. Cites empirical work only.
- **`paper1-subdoxastic/` is the reference.** The full formal treatment: modal
  operator, neighborhood and impossible-worlds semantics, the sub-doxastic
  characterisation, and the objections literature. It is kept for citation and for
  the author's own use, and is **not** to be submitted alongside the other — two
  arXiv submissions arguing one thesis reads as duplicate submission.

Shared content is deliberate and safe as long as only one is posted. The grounding
definition, the taxonomy, and the independence proposition appear in both.

## Layout

The three directories are **fully independent**. Each carries its own
`main.tex`, `macros.tex`, `refs.bib`, `sections/`, `Makefile`, and `figs/`.
Nothing is shared, and no directory can break another's build.

    paperN-*/
      main.tex          preamble, title, abstract, \input list
      macros.tex        every multi-letter operator, defined once
      sections/*.tex    one file per \section
      refs.bib          bibliography source (arXiv reads the .bbl, not this)
      figs/             must already be .pdf/.png/.jpg
      build/            latexmk output; gitignored
      Makefile          build + tarball assembly

**The cost of independence:** notation is kept in step across the three papers
**by hand**. `macros.tex` is duplicated, not shared. Before renaming an
operator, grep the sibling directories:

    grep -rn 'OperatorName' papers/*/

## Commands

Run these inside a paper directory.

    make          build build/main.pdf
    make watch    continuous rebuild
    make arxiv    build, then assemble arxiv-main.tar.gz and list contents
    make check    embedded fonts (Type 3 = problem) + box count
    make clean    remove aux files, .bbl, tarball

In VS Code, saving `main.tex` builds automatically (LaTeX Workshop), and
cmd-click moves between the PDF preview and the source via SyncTeX.

## Bibliography: currently disabled

None of the three papers cites anything yet, and `bibtex` errors on a document
with zero `\cite` commands. So `\bibliographystyle`/`\bibliography` are
commented out in each `main.tex`, and `make arxiv` copies a `.bbl` only if one
exists.

`refs.bib` carries six verified entries (Rosenblatt 1958, Rumelhart 1986,
LeCun 1998, Hochreiter 1997, Vaswani 2017, Brown 2020). Uncomment both lines
in `main.tex` once real citations land. **A submission with no bibliography
will not survive review** — see the TODO list in each paper.

## arXiv checklist

- [ ] Submit **source**, not PDF. arXiv's stated order of preference is
      (La)TeX first, PDF second.
- [ ] Include `main.bbl`. The `.bbl` filename must match the main `.tex`
      filename. `make arxiv` copies it out of `build/` when it exists.
- [ ] If you switch to biblatex: the document and the `.bbl` must be produced
      by the **same** program — both Biber or both BibTeX, never mixed.
- [ ] Figures already converted to `.pdf`, `.png`, or `.jpg`. arXiv does no
      on-the-fly figure conversion.
- [ ] Also include `.ind` (makeindex), `.gls`/`.nls` (glossaries) if used.
- [ ] No `\pdfoutput` — that advice is obsolete; use `ifpdf` for branching.
- [ ] No `minted` or anything needing `--shell-escape`. Use `listings`.
- [ ] No embedded JavaScript, animated GIFs, or movies — auto-rejected.
- [ ] No hidden files or directories in the tarball (deleted on announcement).
      The `arxiv` target excludes dotfiles and macOS `._` resource forks.
- [ ] `macros.tex` is included in the tarball. It is `\input` rather than
      packaged as a `.sty` specifically so it is not a "non-TeX-Live package".
- [ ] No double-spaced "referee" mode.
- [ ] Fonts embedded, no Type 3 bitmaps (`make check`).
- [ ] Zero overfull boxes (`make check`) — an overfull box puts text in the
      margin.

**Verify the TeX Live version before submitting.** The earlier draft of this
checklist asserted arXiv compiles against TeX Live 2025; this machine runs
2026. Confirm against arXiv's current TeX submission guidance rather than
trusting this line — the gap is usually harmless but is worth two minutes.

## Before you can submit

**Endorsement.** First-time arXiv submitters need an endorsement before their
initial submission, and again when posting to a new category. It is granted
automatically if you claim ownership of a paper a co-author already submitted,
or if you register with an institutional email address meeting arXiv's
criteria. Otherwise you request a personal endorsement: start the submission,
arXiv identifies which endorser you need and emails you a shareable link, and
you approach an established author in the subject area. Budget time for this.

**Category.** Each paper still needs a primary category. For paper 1, `cs.AI`
or `cs.CL` is natural, with `cs.LG` as a cross-list; paper 1 is also a
plausible `cs.CY` fit given the institutional framing. Paper 3 may want
`quant-ph` as a cross-list. The primary category determines whose endorsement
you need, so it is worth settling early.

**License.** arXiv asks you to pick a license at submission time. Choose one
consistent with `LICENSE-DOCS.md` at the repository root rather than deciding
in the submission form.

**Author email.** All three `main.tex` files carry `TODO@example.com`.
