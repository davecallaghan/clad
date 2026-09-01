# Evaluation: the hyperintensional modal-operator manuscript

A review of the second manuscript brief (LLM assertion as a hyperintensional
non-normal modal operator) against (a) formal epistemology as actually
practised and (b) what is known empirically about LLM behaviour. Written to be
read alongside `main.tex`.

Citations are marked for reliability:

- **[V]** verified this session against DBLP or Crossref
- **[H]** high confidence, not machine-verified
- **[?]** verify before citing — I could not confirm it

---

## 0. Verdict in brief

The manuscript has two genuinely publishable ideas and one load-bearing
inference that does not go through.

**Keep, and lead with:**

1. **The hallucination redefinition.** `Hallucination(φ) =def O_M φ ∧ ¬G_M φ`,
   with no truth condition, plus the 2×2 and the claim that truth-matching
   benchmarks misclassify both off-diagonal cells. This is the most defensible
   and most useful contribution.
2. **RE-failure as the diagnostic.** Congruentiality failure is the one axiom
   failure that is distinctive to LLMs, empirically well-evidenced, and
   formally consequential.

**Repair before submitting:**

3. **The central inference is a level confusion.** "`O_M` is non-normal,
   therefore M is not doxastic" proves too much — it would show humans are not
   doxastic either. §2.1.
4. **One outright technical error.** RE-failure rules out neighbourhood
   semantics *as well as* Kripke semantics. Every neighbourhood frame validates
   RE. §2.2.
5. **Two mislabels.** S5 is the wrong logic to attribute to knowledge (§2.3);
   the ungrounded-but-true cell is a lucky guess, not a Gettier case (§2.7).
6. **Several axiom failures are artifacts of your own threshold definition**
   and are shared with every threshold account of human belief. Claiming them
   as evidence of LLM exceptionalism is a trap. §2.5.
7. **`G_M` is under-specified in a way that collides with Paper 1.** §3.2.
8. **The credence threshold `t` contradicts the sub-doxastic thesis.** §3.3.

---

## 1. First problem: you have two papers making one claim

Before any content question. Paper 1 (`main.tex`, drafted) and this manuscript
argue overlapping theses with different machinery:

| | Paper 1 | This manuscript |
|---|---|---|
| Core claim | `Says_M(p｜x) ⇏ K_M(p)`, `⇏ True`, `⇏ Warranted` | `O_M` is non-normal and hyperintensional, hence neither S5 nor KD45 |
| Sub-doxastic framing | asserted, Stich uncited | asserted, Stich 1978 cited |
| Apparatus | indexed truth, non-entailment chains | axiom-failure table, neighbourhood/impossible worlds |
| Hallucination | absent | the second contribution |
| Formal results | none | none |
| Terminus | institutional warrant (sets up Papers 2–3) | evaluation methodology |

Both say "LLMs are sub-doxastic predictive systems, not epistemic agents." A
referee who sees both will call it self-overlap, and arXiv cross-listing makes
that visible.

**Recommendation: merge, don't parallel-publish.** The modal apparatus is
*better* than what Paper 1 currently has. Paper 1's §The Non-Entailment Thesis
is a list of eight `⇏` claims with no semantics behind them; a formal
epistemology referee will ask what logic makes those precise, and this
manuscript is the answer. Concretely:

- **Into Paper 1:** the output operator, the axiom-failure analysis, the
  hyperintensionality result, the Stich framing, and the three objections.
  This replaces Paper 1's §Non-Entailment Thesis and §Language Models as
  Sub-doxastic Systems rather than sitting beside them.
- **Into Paper 1 as well:** the *definition* of hallucination. It is an
  epistemological claim about what makes an assertion defective, which is
  Paper 1's subject.
- **Into Paper 2:** the *measurement* programme — `P(¬G_M φ ｜ O_M φ)` via
  attribution audit, and the benchmark-misclassification argument. That is
  evaluation methodology inside a governance architecture, which is Paper 2's
  subject by your own scope rules.

That split keeps Paper 1 from becoming Paper 2 while letting the formal work
land where it does the most good.

**Notation collisions to fix on merge.** These are hard conflicts, not style:

| Symbol | Paper 1 | This manuscript |
|---|---|---|
| `p` | a proposition | a **prompt-form** |
| `M` | the model | also the **monotonicity axiom** `□(φ∧ψ)→(□φ∧□ψ)` |
| assertion | `Says_M(p ｜ x)` | `O_M φ` |
| evidential support | `Supports(E,p,c)` | `G_M φ` |
| proposition variable | `p` | `φ` |

`p` and `M` each doing double duty is fatal in a formal paper. Suggested
resolution: propositions `φ`, prompt-context `x` (Paper 1's variable), model
`M`, and rename the Chellas axioms with a prefix (`Ax-M`, `Ax-C`, `Ax-N`) or
cite them as RM/RE/RK rules only. Keep `Says_M`/`Supports` as the readable
names and introduce `O_M`/`G_M` as explicit abbreviations in the formal
sections, so the trilogy shares one vocabulary.

---

## 2. Formal epistemology

### 2.1 The central inference does not go through

This is the most serious problem. The argument is:

> knowledge is normal (S5); belief is normal (KD45); `O_M` is non-normal;
> therefore LLMs are neither epistemic nor doxastic systems.

`K` and `B` are **attitude** operators. `O_M` is an **output** operator — a
speech-act/generation operator. Showing that the logic of a system's *outputs*
is non-normal tells you nothing about whether the system has attitudes, because
these are different types of object.

The proof that this proves too much: define `O_H φ` for a human H — "H asserts
φ." `O_H` fails N (humans don't assert all tautologies), K (assertion isn't
closed under known implication), 4 and 5 (nobody asserts that they assert), and
RE spectacularly — framing effects on logically equivalent presentations are
among the best-replicated findings in psychology (Tversky & Kahneman 1981
[H]). Yet humans are doxastic systems. So non-normality of the assertion
operator cannot entail absence of belief.

You have anticipated a *version* of this as "the human-parallel objection," but
you have located it at the psychological level (framing effects, closure
failure). The real objection is structural: **your formalism is not aimed at the
thing your thesis is about.** A referee in formal epistemology will see this
before they see the framing-effect point.

**What to do.** Two honest routes:

*Route A — narrow the claim.* Argue only that LLM *assertion* is not governed
by the norms that make human assertion knowledge-apt. That is a real, defensible
thesis, it connects to Williamson's knowledge norm of assertion (Williamson
2000 [H]) and Brandom on assertion as commitment (Brandom 1994 [H]), and the
modal apparatus supports it directly. You lose the headline "LLMs are not
doxastic systems" and gain an argument that survives review.

*Route B — supply the missing bridge.* Keep the strong claim, but add a premise
that for M there is nothing behind the output: no state that plays the
functional role of belief. That premise is **architectural and empirical, not
formal**, and it is exactly what the interpretability literature contests
(§3.5). If you take this route the modal logic becomes a symptom, not the
argument, and the paper's centre of gravity moves to §3.5.

Route A is the stronger paper. Route B is the more ambitious one and needs the
empirical section to carry real weight.

### 2.2 RE-failure rules out neighbourhood semantics too

A technical error, and worth catching before a logic referee does.

The brief says RE-failure "rules out Kripke semantics and requires neighborhood
models (Chellas 1980) or impossible-worlds semantics." But **every
neighbourhood frame validates RE.** In neighbourhood semantics `□φ` holds at
`w` iff `‖φ‖ ∈ N(w)`, where `‖φ‖` is the truth set of φ. Logically equivalent
φ and ψ have the *same* truth set, so `□φ ↔ □ψ` is automatic. RE is valid on
every neighbourhood model, full stop.

In Chellas's own hierarchy, logics closed under RE are the **classical** logics,
and neighbourhood models are the semantics *for* classical logics. Neighbourhood
semantics is the right tool for dropping K and N — that is, for
non-normal-but-classical operators. It cannot represent hyperintensionality.

So if RE genuinely fails, your options are:

- **Impossible worlds** — Nolan 1997 [V], Berto & Jago 2019 [H] (the standard
  modern treatment). But note Bjerring 2011 [V] proves an impossibility result
  for impossible-worlds models of non-omniscient agents; you should engage it
  rather than treat impossible worlds as a free lunch.
- **Awareness structures** — Fagin & Halpern 1987 [V] (*Artificial
  Intelligence*; note **1987**, not 1988). This is the canonical CS solution to
  logical non-omniscience and is arguably the best fit: awareness of a *sentence
  under a presentation* is very close to prompt-form sensitivity.
- **Topic-sensitive / subject-matter semantics** — Berto 2022 *Topics of
  Thought* [H], Yablo 2014 *Aboutness* [H]. Berto's topic-sensitive intentional
  modals are the closest existing formalism to "the same proposition presented
  under a different form gets a different response," which is precisely your
  phenomenon.

My recommendation is awareness structures or topic-sensitivity, not impossible
worlds — they were designed for the resource-bounded/presentation-sensitive
case you actually have, and they carry less metaphysical freight.

### 2.3 S5 is the wrong logic to attribute to knowledge

"Both knowledge (S5) and belief (KD45) are normal operators" — the normality
claim is right, the S5 attribution is not, and it is the kind of thing that
costs credibility in the first two pages.

S5 includes axiom 5, negative introspection: `¬Kφ → K¬Kφ`. That is widely
rejected for knowledge, and rejecting it is close to consensus in epistemic
logic. Hintikka 1962 [H] used S4. Lenzen 1978 [H] surveys the arguments. S5 is
standard for *information* in game theory and distributed systems, not for
knowledge in epistemology.

Fix: say "the S4/S5 family," note that the specific axioms are contested, and
observe that **nothing in your argument needs them** — you need only normality,
which is common to the whole family. That makes the claim both weaker and
safer, at no argumentative cost.

### 2.4 T-failure is missing, and it is the entire epistemic claim

The axiom list is N, K, M, D, 4, 5, RE. **T is absent** — `□φ → φ`,
veridicality.

T-failure is the cleanest possible argument that LLM output is not knowledge:
models assert falsehoods, so `O_M φ → φ` is invalid, so `O_M` is not a
knowledge operator. No modal sophistication required.

This matters for how you frame the paper. The heavy apparatus is not needed for
the epistemic claim, which is nearly trivial. It is needed only for the
**doxastic** claim. Say so explicitly: it sharpens the contribution and stops a
referee wondering why eight axioms are being marshalled to establish something
T alone settles.

### 2.5 Which axiom failures are actually distinctive

A trap. Defining `O_M φ` as "asserts φ above credence threshold t" makes
several of your axiom failures *follow from the threshold* rather than from
anything about LLMs.

- **C / agglomeration fails for any threshold < 1.** If each of φ₁…φₙ passes
  threshold t but their conjunction does not, the operator fails C. This is the
  **lottery and preface paradox** structure (Kyburg [H], Makinson 1965 [H]), and
  it afflicts every threshold account of *human* belief identically.
- **N is not obviously violated.** Does the model assert tautologies above
  threshold? For simple ones, plausibly yes. N-failure needs evidence, not
  stipulation.
- **K-failure is shared** with all resource-bounded reasoners; it is the
  logical omniscience problem (Fagin, Halpern, Moses & Vardi 1995 [V]), not an
  LLM discovery.
- **D-failure is real and well-evidenced** — models self-contradict. Cite
  Elazar et al. 2021 [H] and Mündler et al. 2024 [H].

**Only RE-failure is genuinely distinctive**, because human belief is standardly
*modelled* as congruential even though human assertion is not, whereas for LLMs
the presentation-sensitivity is systematic, measurable, and architectural.

Restructure accordingly: state N/K/C/D failures briefly as scaffolding, flag
which are threshold artifacts, and spend the section on RE. A referee who spots
you claiming the lottery paradox as evidence of machine non-agency will not be
gentle.

### 2.6 Axioms 4 and 5 are ill-typed for an output operator

`O_M φ → O_M O_M φ` reads "if M asserts φ then M asserts that M asserts φ."
Iterating an output operator is not so much false as ungrammatical: the operand
of `O_M` should be a proposition, and "M asserts φ" is a fact about M, not
something M was prompted about.

This is the §2.1 level confusion showing up locally. Axioms 4 and 5 were
designed for attitude operators with introspective access. Applying them to an
output operator produces claims that fail for uninteresting reasons. Either drop
them, or note explicitly that their ill-typedness is itself evidence that `O_M`
is not an attitude operator — which is a legitimate and rather elegant move,
but only if you make it deliberately.

### 2.7 The Gettier claim is a mislabel, and the repair is better

The brief says an ungrounded assertion that happens to be true "remains a
hallucination (a Gettier structure transposed to assertion)."

That is not a Gettier structure. Gettier cases are *justified* true beliefs
that fail to be knowledge; the whole point is that adding justification to true
belief is insufficient. Your cell is `O_M φ ∧ ¬G_M φ ∧ φ` — **un**grounded,
hence unjustified. That is a lucky guess: the pre-Gettier counterexample to
"knowledge = true belief," and the thing justification was introduced to
exclude. The right vocabulary is veritic luck (Pritchard 2005 [H]).

**The repair is more interesting than the original.** The Gettier structure is
real but it is located in the *recipient*, not the model. The user encounters a
fluent, plausible, well-formed assertion; that fluency functions as apparent
justification; the assertion is true; the user forms a justified true belief
that is not knowledge, because the justification is disconnected from the
truth-maker. That is a textbook Gettier case, and it is generated *by* the
ungrounded-true cell.

This is a much better result, because it explains why the ungrounded-true cell
is harmful rather than merely mislabelled, and it connects to the testimony
literature — Lackey 2008 [H], Goldberg 2010 [H] — and straight into Paper 1's
"unlicensed testimony" framing. Take this repair; it strengthens the paper.

### 2.8 The Stich analogy fails on Stich's own criterion

Stich 1978 [V] (*Philosophy of Science*) individuates sub-doxastic states by
**inferential isolation**: they are encapsulated, they do not interact
inferentially with the agent's full belief set, and they are inaccessible to
consciousness. His examples are grammar-parsing and depth-perception
mechanisms.

Two problems for the transfer.

First, **LLMs are not inferentially isolated — arguably the opposite.** A model
will chain inferences across arbitrary domains within a forward pass; content is
globally available to the computation. On Stich's actual criterion, LLM
representations look *more* integrated than sub-doxastic states, not less. The
term fits the conclusion you want but not the diagnostic Stich supplies.

Second, **sub-doxastic is a contrast drawn *within* an agent** that also has
beliefs. That is what gives the term its bite. Applying it to a whole system
that has no beliefs at all is a category stretch, and you have flagged this in
the brief as "the limits of the Stich analogy" — good, but the limits are
sharper than the brief suggests.

**A better primary framework.** What LLM output actually lacks is not
inferential integration but the **constitutive aim of belief**: belief is
constitutively truth-directed. See Shah & Velleman 2005 [V] (*The Philosophical
Review*) and Wedgwood 2002 [H]. The corresponding claim for output is that LLM
assertion is not governed by the norm of assertion (Williamson 2000 [H]).

This is also where Frankfurt's technical sense of **bullshit** — assertion
without regard for truth — becomes directly relevant, and where you must engage
Hicks, Humphries & Slater 2024 [V] (*Ethics and Information Technology*),
"ChatGPT is bullshit." That paper is your nearest competitor: your
`O_M φ ∧ ¬G_M φ` is a formalisation of Frankfurt-bullshit, and a referee will
know it. There is an active debate around it — Humphries, Hicks & Slater have a
2026 reply in *Philosophy & Technology* [V] — so engaging it positions you in a
live conversation rather than looking unaware of it.

My suggestion: keep Stich for the *name* (sub-doxastic is a good label and has
precedent), but ground the *argument* in constitutive-aim and assertion-norm
literature, and say plainly that you are borrowing Stich's term while departing
from his individuation criterion.

### 2.9 There is no theorem — and there is a cheap one available

For a formal epistemology venue this is a real weakness. The manuscript has
definitions and claims of axiom failure, but no result. *Journal of
Philosophical Logic* or *Review of Symbolic Logic* would want a soundness or
completeness result.

Here is a proposition you can actually prove, in half a page, that directly
supports your second contribution.

> **Proposition (accuracy and grounding are independent).** For a system's
> assertions, let `a = P(φ ｜ O_M φ)` be accuracy and `g = P(G_M φ ｜ O_M φ)` be
> the grounding rate. Then every pair `(a, g) ∈ [0,1]²` is realisable.

*Proof.* Let the four cells of the 2×2 carry masses `m_GT, m_GF, m_UT, m_UF`
summing to 1. Then `a = m_GT + m_UT` and `g = m_GT + m_GF`. Given any target
`(a,g)`, choose `m_GT` with `max(0, a+g-1) ≤ m_GT ≤ min(a,g)` — non-empty for
all `(a,g) ∈ [0,1]²` — then set `m_GF = g - m_GT`, `m_UT = a - m_GT`, and
`m_UF = 1 - a - g + m_GT`, all non-negative by construction. ∎

The proof needs `G` not to entail truth, which your own 2×2 grants: the
grounded-false cell is exactly faithful transmission of an erroneous source.

> **Corollary.** No truth-matching metric bounds the hallucination rate
> `1 - g`. A benchmark reporting accuracy alone is consistent with any
> hallucination rate whatsoever.

That corollary *is* your benchmark-misclassification claim, upgraded from
observation to result. A second corollary worth stating: accuracy can improve
while the hallucination rate worsens — which is a plausible reading of what
optimising for helpfulness does.

Elementary, but it gives the paper a labelled formal contribution and makes the
evaluation section rigorous rather than rhetorical.

---

## 3. The data science

### 3.1 RE-failure is your best-evidenced claim

Good news: the empirical literature supports congruentiality failure strongly,
and this is where the paper is on firmest ground.

- **Berglund et al. 2024 [V]** (ICLR), *The Reversal Curse: LLMs trained on
  "A is B" fail to learn "B is A."* This is your single best citation. For
  identity statements, `A is B` and `B is A` are logically equivalent, and
  models systematically fail to transfer. That is RE-failure with a clean
  logical form, not a vague framing effect.
- **Sclar et al. 2024 [V]** (ICLR), *Quantifying Language Models' Sensitivity
  to Spurious Features in Prompt Design.* Reports large performance spreads
  across semantically equivalent formatting choices. This is RE-failure
  measured at scale. Check the exact spread figure against the paper before
  quoting a number.
- **Webson & Pavlick 2022 [H]** (NAACL), *Do Prompt-Based Models Really
  Understand the Meaning of Their Prompts?* — models perform comparably with
  irrelevant or misleading prompts, which decouples response from propositional
  content.
- **Zhao et al. 2021 [H]** (ICML) and **Lu et al. 2022 [H]** (ACL) on
  in-context example ordering.
- **McCoy et al. [H]**, *Embers of Autoregression* — output-probability
  sensitivity. Verify venue and year.

One caveat worth pre-empting. A critic will say prompt-form sensitivity is a
*performance* phenomenon that averages out, and that the underlying model
assigns stable probabilities to propositions. The Reversal Curse is your best
answer, because it is not noise that averages out — it is a systematic,
architecturally explicable asymmetry that persists under scaling.

### 3.2 `G_M` is under-specified, and this is where it collides with Paper 1

The most consequential technical gap. `G_M φ` is "φ is supported by M's
evidential base E." What is `E`?

- **If `E` is the training corpus**, then nearly everything is grounded — some
  text somewhere supports almost any plausible claim — so `¬G_M φ` is close to
  empty and the hallucination rate collapses toward zero. The definition loses
  its teeth.
- **If `E` is the retrieved context at inference time**, then the definition
  applies only to retrieval-augmented systems, and says nothing about a bare
  model.

Paper 1 already settles this, and in the second direction: it argues
`TrainedOn(M,E) ⇏ Supports(E,p,c)` and `⇏ Traceable(E)`. Parameter influence is
not an audit trail. So the two documents are inconsistent unless `E` is
explicitly the **citable, versioned, inference-time** evidential base.

**This is a feature, not a bug, if you say it out loud.** Bite the bullet:
hallucination-versus-error is *not decidable* for a bare model, because a bare
model has no identifiable `E` against which grounding could be assessed. That
is a strong independent argument for Paper 2's architecture — the distinction
only becomes measurable once the system is built to make `E` explicit. It turns
an apparent inconsistency into the bridge between Papers 1 and 2.

A second worry to address: is defining hallucination purely by `¬G` the right
*normative* choice? Grounded-but-false output still misleads the user and still
produces bad decisions. The defence is that the two failures have different
remedies — error means fix the corpus, hallucination means fix the model or the
grounding pipeline — so the distinction is diagnostically valuable even though
both are harmful. State that explicitly; otherwise the definition looks like it
is excusing a real failure mode.

### 3.3 The credence threshold `t` contradicts the sub-doxastic thesis

`O_M φ` is defined as assertion "above credence threshold t." But attributing a
**credence** to M is attributing a graded doxastic state — the very thing the
paper denies M has. This is the same problem I flagged in the superseded Paper 1
draft, which identified `Bel_M` with `Cred_M` outright.

Fix, and it costs nothing: define the threshold over the model's **output
distribution**, not its credence. `O_M φ` iff `P_M(φ ｜ x) > t`, where `P_M` is
the sampling/decoding distribution — a purely behavioural quantity, measurable,
and attributing no attitude. Paper 1's `GenDisp_M(p ｜ x) = Pr_M(utter(p) ｜ x)`
already has exactly the right shape. Use it, and the two documents align.

### 3.4 Prior art: this 2×2 already exists under another name

Your grounded/true distinction is the NLP field's **faithfulness vs
factuality** distinction. A referee from ML will say so immediately, so cite it
first and claim the right novelty.

- **Maynez et al. 2020 [H]** (ACL), *On Faithfulness and Factuality in
  Abstractive Summarization* — origin of the split, plus intrinsic/extrinsic
  hallucination.
- **Ji et al. 2023 [H]** (*ACM Computing Surveys*), *Survey of Hallucination in
  Natural Language Generation* — establishes the distinction as standard.
- **Huang et al. [?]** (*ACM TOIS*), factuality-vs-faithfulness hallucination
  taxonomy. I could not verify year or venue; check before citing.

Your actual novelty is not the distinction but the **normative asymmetry**: the
claim that only `¬G` constitutes hallucination, that this is an epistemological
rather than engineering claim, and that it follows from a theory of warranted
assertion. Frame it that way and the prior art becomes support rather than
scoop.

### 3.5 The interpretability literature is your real adversary — and it can be turned

If you take Route B in §2.1, this section carries the paper, and it is where the
strongest counter-evidence lives. A growing body of work claims to find
truth-tracking internal structure:

- **Burns et al. 2023 [H]** (ICLR), *Discovering Latent Knowledge in Language
  Models Without Supervision* — unsupervised probe for truth directions.
- **Azaria & Mitchell 2023 [H]** (EMNLP Findings), *The Internal State of an
  LLM Knows When It's Lying.*
- **Marks & Tegmark [H]**, *The Geometry of Truth.* Verify venue and year.

If there is a linear "truth direction" in the activations, the claim that
nothing plays a belief-like role gets harder.

**Turn it.** Azaria & Mitchell's result is that the model's internal state
registers the falsity of what it is *nonetheless asserting*. That is a
**dissociation between internal representation and assertion** — which is your
thesis, stated in the adversary's own data. A state that tracks truth but does
not govern assertion is precisely a state that fails the constitutive-aim
condition on belief (§2.8). The evidence for internal truth-tracking is
evidence that representation and commitment come apart in these systems.

Then use the critical literature to blunt the strong reading:

- **Levinstein & Herrmann 2024 [V]** (*Philosophical Studies*), *Still no lie
  detector for language models: probing empirical and conceptual roadblocks.*
  Your key ally: argues current probing methods do not establish belief
  representations.
- **Herrmann & Levinstein 2024 [V]** (*Minds and Machines*), *Standards for
  Belief Representations in LLMs.* Sets out what would have to be shown. Engage
  its criteria directly — meeting or explicitly failing them is the most
  credible thing you can do in this section.

I looked for a Goldstein & Levinstein paper titled *Does ChatGPT Have a Mind?*
and could not verify it on Crossref or arXiv. I may be misremembering; do not
cite it on my word. The two verified Levinstein/Herrmann items above cover the
same ground.

### 3.6 Calibration evidence cuts both ways

Your `¬K_A(p) → K_A(¬K_A(p))` meta-knowledge condition (Paper 1's omniscience
section, and axiom 5 here) has a literature that is genuinely mixed, and the
title of the main result cuts against you:

- **Kadavath et al. 2022 [H]**, *Language Models (Mostly) Know What They Know.*
  You must engage this; it reports non-trivial self-evaluation ability.
- **Lin, Hilton & Evans 2022 [H]**, *Teaching Models to Express Their
  Uncertainty in Words.*

The reply available to you is the right one and is stronger than a flat denial:
partial calibration on a benchmark distribution is not the reliable
boundary-recognition your condition demands, and RLHF is documented to *degrade*
calibration (the GPT-4 system card shows this [H] — verify the specific figure).
Aggregate calibration and per-claim boundary recognition are different
properties, and only the latter supports abstention in a consequential setting.

### 3.7 Measurement: AIS is the right instrument

`P(¬G_M φ ｜ O_M φ)` via attribution audit is well-founded, and the
operationalisation already exists:

- **Rashkin et al. 2023 [V]** (*Computational Linguistics*), *Measuring
  Attribution in Natural Language Generation Models* — the AIS (Attributable to
  Identified Sources) framework. This is your metric. Cite it as the
  operationalisation rather than proposing a new one.
- **Liu, Zhang & Liang 2023 [H]**, *Evaluating Verifiability in Generative
  Search Engines* — found a low fraction of generated statements fully supported
  by their own citations. A strong empirical anchor for a non-trivial
  hallucination rate under your definition. Verify the exact percentage.
- **Honovich et al. 2022 [H]**, *TRUE: Re-evaluating Factual Consistency
  Evaluation.*
- **Farquhar et al. 2024 [V]** (*Nature*), *Detecting hallucinations in large
  language models using semantic entropy.*

Two honest caveats to include. Attribution judgements require human raters or a
strong NLI model, and inter-annotator agreement on attribution is moderate — so
`P(¬G ｜ O)` is estimated with real noise. And note a tension worth a paragraph:
semantic entropy deliberately clusters outputs by *meaning* via bidirectional
entailment, i.e. it quotients out exactly the hyperintensional variation your
first contribution says is essential. Whether that is a limitation of the metric
or a concession by your thesis is a good question, and addressing it will read
as confidence.

### 3.8 Formal results on hallucination you should not ignore

Two recent results bear directly on whether hallucination is structural, which
is Paper 1's opening claim:

- **Kalai & Vempala 2024 [V]** (STOC; arXiv 2023), *Calibrated Language Models
  Must Hallucinate.* A genuine theorem: for facts appearing rarely in training,
  calibration *forces* a positive hallucination rate. This is strong formal
  support for "hallucination is structural" and it is a much better citation
  than an appeal to the cross-entropy objective.
- **Kalai et al. [?]**, *Why Language Models Hallucinate* — argues training and
  evaluation incentives reward guessing over abstention. Directly supports your
  benchmark-misclassification claim. I could not verify venue or date; check it.
- **Xu, Jain & Kankanhalli [?]**, *Hallucination is Inevitable: An Innate
  Limitation of LLMs.* Cite with care: its formalisation is contested and rests
  on a diagonalisation over computable functions, so the impossibility is
  weaker than the title suggests. Do not lean on it.

Kalai & Vempala is the important one. It gives you a formal reason why the
ungrounded cell cannot be driven to zero by better training, which is why the
architectural response of Papers 2–3 is needed.

---

## 4. Objections and replies

### 4.1 Dennett: your own result supplies the reply

Dennett 1971 [H], 1987 [H]: if the intentional stance is predictively
successful, the system has beliefs, and there is no further fact.

Your best reply is already in the paper and you should make it explicitly:
**the intentional stance fails Dennett's own test here.** Its criterion is
predictive success, and RE-failure means belief attributions are not projectible
across paraphrase — "the model believes φ" does not predict what it outputs when
φ is presented differently. Dennett himself allows that the design stance can be
the more predictive one, and for prompt-sensitivity it plainly is.

That is a reply from Dennett's own commitments rather than a rejection of them,
and it uses your first contribution to discharge the objection. Strong.

### 4.2 Human parallel: do not lead with competence/performance

You have allocated this the most space, correctly — it is the strongest
objection. But the planned reply is the weaker of the two available.

**Why competence/performance is a weak reed.** It concedes that human
*performance* is non-normal and posits an idealised competence that is normal.
The critic then makes the symmetric move: posit an idealised LLM competence
too. What blocks it? Chomsky 1965 [H] gives you no principled asymmetry, and the
distinction is independently contested in linguistics. You would be defending a
disputed distinction in order to defend your main claim.

**Lead with the practical-reasoning economy instead** — which you already list
as the second element. Make it primary, and make it three specific asymmetries
rather than one:

1. **Stake.** Human belief feeds action, and false belief is corrected by
   consequences. There is a feedback loop with costs. Model output has no such
   loop within the system.
2. **Cross-context stability.** My belief that Paris is in France persists
   across conversations and constrains unrelated decisions. Model output has no
   commitment that survives the context window.
3. **Accountability.** Assertion is a normative commitment one can be held to —
   Brandom 1994 [H] on commitment and entitlement, Williamson 2000 [H] on the
   knowledge norm. There is no bearer of that commitment here.

These are architectural and normative asymmetries, not statistical ones, so the
critic cannot neutralise them by pointing at human framing effects. And (3) is
the strongest form of your thesis: LLM output may not be assertion at all in the
speech-act sense, because assertion requires a commitment-taking agent. That
also connects directly to Paper 1's "unlicensed testimony."

Keep competence/performance as a secondary remark if you like, but do not rest
the section on it.

### 4.3 Deflationism: the planned reply is right

"Who cares what we call it?" The reply is that the terminological question has
downstream consequences: it changes what you measure (§2.9's corollary — a
benchmark reporting accuracy cannot bound the hallucination rate) and what an
institution may rely on (Paper 2). A distinction with different measurement and
governance implications is not merely verbal. This is fine as planned; the
proposition in §2.9 makes it concrete rather than promissory.

---

## 5. Restructure and word budget

The planned structure is sound. Two changes, following §2.1 and §2.5:

| Section | Words | Notes |
|---|---|---|
| Introduction | 700 | State early that the epistemic claim is trivial (T) and the doxastic claim is the contribution |
| Formal preliminaries | 600 | Compressed, assume literacy. Fix the S5 point here |
| The output operator | 900 | Define over the output distribution, not credence (§3.3) |
| Axiom failures | 1,000 | N/K/C/D brief, flagged as threshold artifacts where they are; RE gets most of it |
| Hyperintensionality | 1,200 | Correct the neighbourhood-semantics error; empirical support from Reversal Curse and FormatSpread |
| Grounding and the 2×2 | 1,000 | Define E as inference-time and citable; acknowledge faithfulness/factuality prior art |
| Hallucination as unwarranted assertion | 1,100 | Includes the Proposition and its corollary; the Gettier repair |
| Sub-doxastic systems | 900 | Stich for the name, constitutive-aim for the argument |
| Objections | 1,800 | Human-parallel ~1,000 of it, restructured per §4.2 |
| Implications for evaluation | 600 | AIS; the corollary does the work |
| Conclusion | 400 | |
| **Total** | **~10,200** | |

That is over your 9,000. Honest options: cut the implications section to 300 and
fold it into the hallucination section, or drop axioms 4 and 5 entirely (§2.6
suggests they are ill-typed anyway) for another ~200. I would not cut the
objections. Realistically this is a 10,000-word paper; most venues in the list
below will take that.

---

## 6. Venue

The formal apparatus as it stands is *applied* modal logic — definitions and
diagnosed axiom failures — not new logic. Without a soundness or completeness
result, *Journal of Philosophical Logic* and *Review of Symbolic Logic* are a
poor fit; they will ask for the theorem. The Proposition in §2.9 helps but is
elementary.

Best fits for a ~10,000-word formal-epistemology-plus-philosophy-of-AI paper:

- **Synthese** — publishes both formal epistemology and LLM philosophy; the
  most natural home.
- **Philosophy & Technology** — very receptive to this material, and where the
  bullshit debate is currently running.
- **Minds and Machines** — where Herrmann & Levinstein 2024 appeared; the right
  audience for §3.5.
- **Erkenntnis** — good for the formal side.
- **Episteme** — if you push the testimony and social-epistemology angle from
  §2.7.
- **Ethics and Information Technology** — where Hicks et al. 2024 appeared;
  appropriate if the normative framing leads.

Given that your nearest competitor and your key allies are in *Philosophy &
Technology*, *Minds and Machines*, and *Philosophical Studies*, I would target
**Synthese** first and **Philosophy & Technology** second.

---

## 7. Bibliography

Reliability markers as above: **[V]** verified this session, **[H]** high
confidence unverified, **[?]** verify before citing. I have deliberately
omitted DOIs, since I could not verify them, and omitted anything I could not
substantiate at all.

### Formal epistemology and modal logic

- **[V]** Nolan, Daniel. "Impossible Worlds: A Modest Approach." *Notre Dame
  Journal of Formal Logic*, 1997.
- **[V]** Fagin, Ronald, and Joseph Y. Halpern. "Belief, awareness, and limited
  reasoning." *Artificial Intelligence*, 1987. — Note 1987, not 1988.
- **[V]** Fagin, Halpern, Moses, and Vardi. *Reasoning About Knowledge*. 1995.
- **[V]** Bjerring, Jens Christian. "Impossible worlds and logical omniscience:
  an impossibility result." *Synthese*, 2011. — Engage this before adopting
  impossible worlds.
- **[V]** Shah, Nishi, and J. David Velleman. "Doxastic Deliberation." *The
  Philosophical Review*, 2005.
- **[H]** Chellas, Brian. *Modal Logic: An Introduction*. Cambridge, 1980. —
  Your neighbourhood-semantics source; check the RE/RM/RK hierarchy against
  §2.2 before writing.
- **[H]** Berto, Franz, and Mark Jago. *Impossible Worlds*. Oxford, 2019.
- **[H]** Berto, Franz. *Topics of Thought*. Oxford, 2022. — Topic-sensitive
  intentional modals; likely your best formal fit.
- **[H]** Yablo, Stephen. *Aboutness*. Princeton, 2014.
- **[H]** Hintikka, Jaakko. *Knowledge and Belief*. Cornell, 1962.
- **[H]** Lenzen, Wolfgang. *Recent Work in Epistemic Logic*. 1978. — For the
  S4-vs-S5 point.
- **[H]** Williamson, Timothy. *Knowledge and Its Limits*. Oxford, 2000.
- **[H]** Brandom, Robert. *Making It Explicit*. Harvard, 1994.
- **[H]** Wedgwood, Ralph. "The Aim of Belief." *Philosophical Perspectives*,
  2002.
- **[H]** Pritchard, Duncan. *Epistemic Luck*. Oxford, 2005.
- **[H]** Lackey, Jennifer. *Learning from Words*. Oxford, 2008.
- **[H]** Goldberg, Sanford. *Relying on Others*. Oxford, 2010.
- **[H]** Makinson, D. C. "The paradox of the preface." *Analysis*, 1965.
- **[?]** Rantala, Veikko. "Impossible World Semantics and Logical
  Omniscience." *Acta Philosophica Fennica*, 1982. — I believe this exists but
  could not verify; the journal is poorly indexed.
- **[?]** Kyburg, Henry. On the lottery paradox. — Verify which work you want;
  *Probability and the Logic of Rational Belief* (1961) is the usual cite.

### Philosophy of mind and of AI

- **[V]** Stich, Stephen P. "Beliefs and Subdoxastic States." *Philosophy of
  Science*, 1978.
- **[V]** Hicks, Michael Townsen, James Humphries, and Joe Slater. "ChatGPT is
  bullshit." *Ethics and Information Technology*, 2024. — Your nearest
  competitor; note a published correction exists.
- **[V]** Humphries, Hicks, and Slater. "LLMs Bullshit by Design: A Reply to
  Licon." *Philosophy & Technology*, 2026. — Shows the debate is live.
- **[V]** Levinstein, Benjamin A., and Daniel A. Herrmann. "Still no lie
  detector for language models: probing empirical and conceptual roadblocks."
  *Philosophical Studies*, 2024.
- **[V]** Herrmann, Daniel A., and Benjamin A. Levinstein. "Standards for
  Belief Representations in LLMs." *Minds and Machines*, 2024.
- **[V]** Mandelkern, Matthew, and Tal Linzen. "Do Language Models' Words
  Refer?" *Computational Linguistics*, 2024.
- **[V]** Ostertag, Gary. "Language Models and Externalism: A Reply to
  Mandelkern and Linzen." *Computational Linguistics*, 2025.
- **[H]** Dennett, Daniel. "Intentional Systems." *Journal of Philosophy*,
  1971.
- **[H]** Dennett, Daniel. *The Intentional Stance*. MIT, 1987.
- **[H]** Frankfurt, Harry. *On Bullshit*. Princeton, 2005.
- **[H]** Bender, Emily, and Alexander Koller. "Climbing towards NLU: On
  Meaning, Form, and Understanding in the Age of Data." ACL 2020.
- **[H]** Bender, Gebru, McMillan-Major, and Shmitchell. "On the Dangers of
  Stochastic Parrots." FAccT 2021.
- **[H]** Mitchell, Melanie, and David Krakauer. "The debate over understanding
  in AI's large language models." *PNAS*, 2023.
- **[H]** Shanahan, Murray. "Talking About Large Language Models."
  *Communications of the ACM*, 2024.
- **[H]** Shanahan, McDonell, and Reynolds. "Role play with large language
  models." *Nature*, 2023.
- **[H]** Chomsky, Noam. *Aspects of the Theory of Syntax*. MIT, 1965. — For
  competence/performance, which §4.2 advises demoting.
- **[H]** Tversky, Amos, and Daniel Kahneman. "The Framing of Decisions and the
  Psychology of Choice." *Science*, 1981.
- **[?]** Millière, Raphaël, and Cameron Buckner. "A Philosophical Introduction
  to Language Models." — Two-part survey; verify venue and year. Worth reading
  in full before you write.

### Hyperintensionality and congruentiality failure: empirical

- **[V]** Berglund et al. "The Reversal Curse: LLMs trained on 'A is B' fail to
  learn 'B is A'." ICLR 2024. — Your best single citation for RE-failure.
- **[V]** Sclar, Melanie, Yejin Choi, and Yulia Tsvetkov. "Quantifying Language
  Models' Sensitivity to Spurious Features in Prompt Design." ICLR 2024. —
  Verify the exact performance-spread figure.
- **[H]** Webson, Albert, and Ellie Pavlick. "Do Prompt-Based Models Really
  Understand the Meaning of Their Prompts?" NAACL 2022.
- **[H]** Zhao et al. "Calibrate Before Use: Improving Few-Shot Performance of
  Language Models." ICML 2021.
- **[H]** Lu et al. "Fantastically Ordered Prompts and Where to Find Them."
  ACL 2022.
- **[H]** Elazar et al. "Measuring and Improving Consistency in Pretrained
  Language Models." *TACL*, 2021.
- **[H]** Mündler et al. "Self-contradictory Hallucinations of Large Language
  Models." ICLR 2024.
- **[?]** McCoy et al. "Embers of Autoregression." — Verify venue and year.

### Hallucination: definitions, formal results, measurement

- **[V]** Kalai, Adam Tauman, and Santosh S. Vempala. "Calibrated Language
  Models Must Hallucinate." STOC 2024 (arXiv 2023). — The important formal
  result for "hallucination is structural."
- **[V]** Rashkin et al. "Measuring Attribution in Natural Language Generation
  Models." *Computational Linguistics*, 2023. — The AIS framework; your metric.
- **[V]** Farquhar, Kossen, Kuhn et al. "Detecting hallucinations in large
  language models using semantic entropy." *Nature*, 2024.
- **[H]** Maynez et al. "On Faithfulness and Factuality in Abstractive
  Summarization." ACL 2020. — Prior art for your 2×2.
- **[H]** Ji et al. "Survey of Hallucination in Natural Language Generation."
  *ACM Computing Surveys*, 2023.
- **[H]** Honovich et al. "TRUE: Re-evaluating Factual Consistency
  Evaluation." 2022.
- **[H]** Liu, Nelson, Tianyi Zhang, and Percy Liang. "Evaluating Verifiability
  in Generative Search Engines." 2023. — Verify the exact support percentage.
- **[H]** Turpin et al. "Language Models Don't Always Say What They Think."
  NeurIPS 2023. — Chain-of-thought unfaithfulness; relevant to grounding.
- **[?]** Huang et al. "A Survey on Hallucination in Large Language Models."
  *ACM TOIS*. — Verify year and venue.
- **[?]** Kalai et al. "Why Language Models Hallucinate." — Verify venue and
  date; supports the benchmark-incentive argument.
- **[?]** Xu, Jain, and Kankanhalli. "Hallucination is Inevitable: An Innate
  Limitation of Large Language Models." — Cite cautiously; the formalisation is
  contested.

### Internal representations and calibration

- **[H]** Burns et al. "Discovering Latent Knowledge in Language Models Without
  Supervision." ICLR 2023.
- **[H]** Azaria, Amos, and Tom Mitchell. "The Internal State of an LLM Knows
  When It's Lying." EMNLP Findings 2023. — Read as a representation/assertion
  dissociation, per §3.5.
- **[H]** Kadavath et al. "Language Models (Mostly) Know What They Know."
  2022. — Must engage.
- **[H]** Lin, Hilton, and Evans. "Teaching Models to Express Their Uncertainty
  in Words." *TMLR*, 2022.
- **[?]** Marks, Samuel, and Max Tegmark. "The Geometry of Truth." — Verify
  venue and year.

### Foundational

- **[H]** Vaswani et al. "Attention Is All You Need." NeurIPS 2017. — In
  `refs.bib` already.
- **[H]** Brown et al. "Language Models are Few-Shot Learners." NeurIPS 2020. —
  In `refs.bib` already.

---

## 8. What I would do next, in order

1. **Decide Route A or Route B** (§2.1). Everything else depends on it.
2. **Decide merge or parallel-publish** (§1). If merging, the notation table
   there is the first concrete task.
3. **Fix the neighbourhood-semantics claim** (§2.2) and pick a hyperintensional
   framework — I would look at Berto's topic-sensitivity first.
4. **Redefine the operator over the output distribution** (§3.3) and **define
   `E` as inference-time and citable** (§3.2). These two make the manuscript
   consistent with Paper 1.
5. **Write the Proposition** (§2.9). It is half a page and it converts your
   headline evaluation claim into a result.
6. **Read before writing:** Hicks et al. 2024, Herrmann & Levinstein 2024,
   Levinstein & Herrmann 2024, Kalai & Vempala 2024, and Millière & Buckner.
   The first three determine how you must position the paper; the fourth gives
   you your best structural argument; the fifth will surface anything this
   review missed.
7. **Verify every [?] and spot-check every [H]** before it enters `refs.bib`.
