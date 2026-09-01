# Notation map

The single authoritative symbol table is [`notation.tex`](notation.tex). This file
records **what changed and why**, so that converting the remaining markdown chapters
is mechanical rather than a judgement call each time.

## Resolution principle

**Operators, predicates, and functions get readable multi-letter names. Single letters
are reserved for sets and their elements.**

Not a style preference. The governance corpus (`research/*.md`) and the epistemic
corpus (the grounding paper) were written independently and collided on ten symbols.
Most collisions were operator-versus-set, so the principle dissolves them without
either side surrendering a letter it was using well.

## The ten collisions

| Symbol | Was, in CLAD research | Was, in the paper | Now |
|---|---|---|---|
| `O` | obligatory, `O(φ)` | assertion, `O_S(p)` | assertion → `\Assert`. **`O` stays deontic** — see the reversal note below. |
| `F` | forbidden, `F(φ)` | — (`f` = material facts) | `F` stays deontic; `f` keeps material facts (case distinguishes them) |
| `P`, `p` | set of all prompts | a proposition | prompts → `x ∈ X`; `p, q` keep propositions |
| `C`, `c` | operational constraints | resolved context | constraints → `r ∈ R`; `c` keeps context |
| `A` | agents; audit record `A_g(i,t)` | accuracy indicator `A_i` | `\Audit` (set `\AuditSet`), agents `\Agents`; `A_i` keeps accuracy |
| `γ` | governability class `γ(S)` | target grounding rate | `\gov`; `γ` keeps the rate target |
| `S` | control surface `S_x` | the deployed system | surfaces → `σ_x ∈ Σ`; `S` keeps the system |
| `V` | set of audit records | token vocabulary `V*` | `\AuditSet`; `V` keeps the vocabulary |
| `D` | governance domains | defeater search `𝒟` | `\DefeaterSearch`; `D` keeps domains |
| `Φ` | property vocabulary **and** guarantee `Φ(g)` | — | `\guarantee` for the guarantee; `Φ` keeps the vocabulary |

The last row was an **internal** CLAD collision — `Φ` denoted both the atomic property
vocabulary and a component's guarantee. Fixed here as a side effect.

## Reversed: the deontic operators stay

An earlier pass renamed `O(φ)` and `F(φ)` to `\Require` and `\Forbid`, on the argument
that the implementation is a required-property set and a forbidden-property set with a
conflict check by set intersection, and that modal notation promised machinery the model
did not have.

**That was wrong, and reading the prompt-governance chapter closely is what showed it.**

The chapter engages the deontic literature deliberately and at length: why `P_meta` is
not the classical permission operator `P`; why the axiom `O(φ) → P(φ)` does not apply;
Ross's paradox and free-choice permission as the specific paradoxes the restriction
avoids; and — the load-bearing point — that restricting to *atomic* properties with two
operators keeps contradiction detection **polynomial rather than NP-hard or undecidable**.
The chapter states it plainly: "a deliberate design choice for tractability, not an
oversight."

So the fragment is restricted for a stated reason, and the notation is doing real work.
Renaming breaks the argument: "`P_meta` is not the classical deontic permission operator
`P`" is incoherent if obligation has been renamed to `Require`.

The collision that motivated the rename is independently gone. The epistemic corpus's
assertion operator is `\Assert`, so `O` is free.

Macros are `\Obl` and `\Prohib` — readable in the source, rendering as `O` and `F` to
match the literature. `\Permitted` renders as `P_meta`, keeping the corpus's correct
insistence that it is an annotation rather than an operator: no inheritance effect, no
evaluation effect, no role in conflict detection.

**Note on `O`.** Big-O complexity notation also appears, in the cost analysis of the
governed-retrieval chapter. The two are contextually unambiguous — deontic `O` takes a
property identifier from `Φ`, big-O takes a complexity expression — and both conventions
coexist routinely.

## Mechanical substitutions for markdown conversion

Apply in this order when converting a `research/*.md` chapter to LaTeX.

| Find | Replace |
|---|---|
| `O(φ)` | `\Obl(\phi)` |
| `F(φ)` | `\Prohib(\phi)` |
| `P_meta(φ)` | `\Permitted(\phi)` |
| `C`, `C_m`, `C_p`, `C*(l)` | `R`, `R_m`, `R_p`, `R^*(l)` |
| `p ∈ P` (a prompt) | `x \in X` |
| `p ⊨ c` | `x \satisfies r` |
| `γ(S)` | `\gov(\sigma)` |
| `Φ(g)` | `\guarantee(g)` |
| `A_g(i,t)` | `\Audit_g(i,t)` |
| `S_prompt`, `S_output`, … | `\sigma_{\mathrm{prompt}}`, `\sigma_{\mathrm{output}}`, … |
| `V` (audit records) | `\AuditSet` |
| `R(i)` (compliance risk) | `\risk(i)` |
| `A` (agents) | `\Agents` |
| `ver(x,t)` | `\ver(x,t)` |

Everything in the epistemic corpus keeps its symbols except `O_S → \Assert`, which is
already macro-driven, so the change is one line in `notation.tex`.

## Symbols now unambiguous

`O` is retired and unused. `S` is the deployed system and nothing else. `V` is the token
vocabulary. `D` is governance domains. `A` is the accuracy indicator. `γ` is the target
grounding rate. `Φ` is the property vocabulary. `α` is the target accuracy rate — and is
**not** authority standing, which is `\standing`; those two collided inside the paper
itself until this pass.

## Rule for adding a symbol

Add it to `notation.tex` first, check it against the table above, and prefer a named
operator over a letter unless the thing genuinely is a set or an element of one.

## `research/01-notation.md` is superseded, not converted

`book/notation.tex` is the authoritative symbol table. The markdown version is
left unconverted because it contradicts two resolutions recorded above:

- It lists `O(phi), F(phi), P(phi)` as three **deontic modalities** ("obligatory,
  forbidden, permitted"). The logical core is a two-operator fragment `{O, F}`;
  `P_meta` is a governance annotation with no inheritance, evaluation, or
  contradiction-detection role. Appendix A, A.1.6 states this explicitly, and
  `\Permitted` is bound to `P_{meta}` for exactly this reason.
- It uses bare `gamma(S)` for the governability class. The book uses `\gov(S)`,
  because single Greek letters are reserved for sets and their elements.

Converting it would reintroduce both collisions into the front matter.
