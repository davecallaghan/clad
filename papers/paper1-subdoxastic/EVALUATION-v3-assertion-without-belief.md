# Evaluation: "Assertion Without Belief" (v3), and a comparison of the three drafts

Companion to `EVALUATION-modal-operator-manuscript.md`. Same reliability
markers: **[V]** verified this session against DBLP or Crossref, **[H]** high
confidence unverified, **[?]** verify before citing.

Three documents are in play. I refer to them as:

- **D1** — `main.tex` in this directory. The non-entailment paper: indexed
  contextual truth, illicit universalization, conversational omniscience, world
  access, institutional warrant. Drafted, builds clean, 8 pages.
- **D2** — the modal-operator manuscript brief. Assessed in the companion file.
- **D3** — "Assertion Without Belief: Large Language Models as Sub-Doxastic
  Systems." The full manuscript, this message.

**Scope caveat on D3.** Your message was truncated mid-§8.2, at "recruiting its
assertion-st…". I have §§1–8.2 complete and nothing of §8.3 (deflationism), §9
(implications for evaluation), or §10 (conclusion). Everything below concerns
what arrived. The three sections I have not seen include the one most likely to
overlap Paper 2 (§9), so the scope-boundary judgement in §8 of this review is
provisional.

---

## 0. Verdict

D3 is a substantially better paper than D2, and it is a better paper than D1. It
independently fixed four of the eight problems I raised against D2, including
the technical error and the hardest structural one. The prose is publication-grade
and the argument has a spine.

Two things now stand between it and acceptance, and neither is a matter of
polish:

1. **Reasoning models are the falsifier D3 names and then does not confront.**
   §8.2's signature (ii) makes resource-sensitivity the empirical criterion
   separating human competence-with-lapses from LLM non-competence. Models
   trained with RL on verifiable outcomes exhibit precisely the resource-sensitive
   profile D3 assigns to humans. D3 addresses this in one subordinate clause. §3.
2. **There is no engagement with internal representations at all.** The bridge
   premise that makes the whole argument work — "there is no p-invariant attitude
   for the prompt to elicit" (§3.1) — is asserted and never argued, and it is
   exactly what the probing literature contests. §4.

Both are fixable, and the fix for (2) makes the paper stronger rather than
weaker. Beyond those: four surviving problems in sharpened form (§5), two new
problems D3 introduces (§6), three internal inconsistencies (§7), and a
substantial citation debt on claims that are load-bearing and empirical (§8).

---

## 1. What D3 fixed, and credit where it is due

| Finding against D2 | Status in D3 |
|---|---|
| **§2.2** RE-failure rules out neighbourhood semantics too | **Fixed, correctly and independently.** §2 states that to model RE-failure "neighborhood semantics must itself be modified (neighborhoods over sets of formulas or hyperintensions rather than sets of worlds), or impossible worlds must be invoked." §4.2 says plainly that "bare neighborhood semantics still validates RE, since neighborhoods are sets of truth sets." This is exactly right, and §4.2's resolution — neighbourhoods over formulations, with awareness structures as a special case — is the framework I would have recommended. But the **abstract still carries the old error**; see §7.1. |
| **§2.1** The central inference is a level confusion | **Substantially fixed.** §3.1 supplies the missing bridge: "For a genuinely doxastic system, relativisation to p would be a curable indexicality… For M there is no p-invariant attitude for the prompt to elicit." That is Route B from the companion review, and it converts `a fortiori` in §4.4 from a non sequitur into a licensed step. The bridge premise itself is still unargued — §4. |
| **§2.8** Stich analogy fails on Stich's own criterion | **Fixed on the relational prong, and elegantly.** §7.2 concedes that Stich's category is relational, that no personal-level believer exists here, and that therefore "strictly, LLM states are not sub-doxastic in Stich's sense." The "monadic core" reconstruction and "first free-standing sub-doxastic systems" is a genuine conceptual contribution. The isolation prong is not fixed and has become a new problem — §6.1. |
| **§3.3** Credence threshold contradicts the thesis | **Fixed in substance.** §3.1 defines `t` over "output probability (or of a calibrated confidence proxy)." Terminological residue remains — §7.2. |
| **§2.4** T absent | **Half fixed.** T now appears in §2's inventory and §4.4 notes knowledge is factive. But no §3.x demonstrates T-failure, T is absent from the §3.8 table, and the paper still does not make the framing point: T-failure alone settles the epistemic claim, so the apparatus is earning its keep only on the doxastic claim. Saying that would sharpen the contribution at no cost. |
| **§2.7** Gettier mislabel | **Improved, still mislabelled.** §6.3 now concedes "even the justification is absent," which is the concession — but then says "the parallel is exact." Those are inconsistent. See §5.1. |
| **§3.2** `G_M` under-specified | **Improved via a counterfactual condition**, which introduces a measurability problem. See §5.2. |
| **§2.5** Threshold artifacts | **Partly avoided.** D3 wisely drops any C-failure claim, which is where the lottery/preface trap sat. But M-failure has acquired a related problem — §6.2. |
| **§2.9** No theorem | **Not fixed.** "Fact 1" and "Fact 2" are cited standard results. §2.9 of the companion file still offers the cheapest available result and it maps onto D3's §6.4 directly. |
| **§3.4** Faithfulness/factuality prior art | **Not addressed.** No reference to it anywhere in §§5–6. See §8.2. |
| **§4.1** Dennett reply | **Fixed, and better than my recommendation.** The "corpus stance" coinage and the observation that the two stances make divergent predictions *precisely at the RE-failures* is a stronger version of the reply I proposed. §8.1 is the best section in the paper. |
| **§4.2** Human parallel: don't lead with competence/performance | **Fixed by a better route than I suggested.** D3 keeps competence/performance as the frame but disciplines it with three independently checkable signatures and explicitly confronts the immunising-stratagem worry with falsifiability conditions. That is dialectically superior to my advice, because the objection is *posed* in competence/performance terms and the reply should meet it on that ground. Signatures (i) and (ii) have empirical problems — §5.3, §3. |

That is a strong revision record. The rest of this review is about what remains.

---

## 2. The load-bearing wall

One structural observation before the specific problems, because it determines
where review effort should go.

D3's argument has a single point of failure. §3.7 concedes that RE-conformity
for `O_M` is "a contingent achievement of training coverage" and that what
distinguishes it from validity is that "RE-validity requires exceptionlessness
in principle." Human RE-conformity is *also* an empirical tendency with
principled exceptions — framing effects. So the difference between `O_M` and
human belief cannot be read off the behavioural record; it must come from the
competence/performance argument in §8.2.

Therefore: **§8.2 carries the entire paper.** Sections 3 and 4 establish that
`O_M`'s behavioural profile is non-normal, which nobody will dispute. The thesis
that this is *constitutive* rather than performance-level rests wholly on the
three signatures. If a referee breaks any of the three, the paper's conclusion
degrades from "LLMs are not doxastic systems" to "LLM assertion behaviour is
non-normal, as is human assertion behaviour" — which is not publishable as a
thesis.

D3 does not say this, and it should. Signposting that §8.2 is where the argument
is won or lost is a sign of control, and it directs the referee's attention to
the section where the work has actually been done rather than letting them
imagine the axiom table is the argument.

It also means the two gaps below are not peripheral. They are attacks on §8.2.

---

## 3. Biggest gap: reasoning models are the named falsifier

§8.2's signature (ii) is:

> Human closure failures are modulated by exactly the variables that indicate a
> bounded competence: they diminish with attention, incentive, time, expertise,
> and cognitive unloading (pencil and paper)… LLM RE-failures show a different
> modulation profile: they track distributional frequency of the formulation,
> not resource analogues.

And §8.2's falsifiability clause says the argument would come out the other way
for a system exhibiting "resource-sensitive rather than frequency-sensitive
failure profiles."

Models trained with reinforcement learning on verifiable outcomes — the
inference-time-compute family — are the obvious instance. Their accuracy on
closure-heavy tasks (mathematics, formal logic, multi-step deduction) scales
with the number of reasoning tokens spent. That is resource-sensitivity in
precisely D3's sense: more time, better closure. Extended deliberation is the
machine analogue of pencil and paper, which D3 itself names as a human
competence-signature.

D3's answer is one clause: extra computation "helps only insofar as it
stochastically routes generation through formulations the training distribution
handles well — and characteristically produces confabulated rationales for
formulation-driven answers." Two problems. First, it is an assertion with no
evidence offered, and it is a strong empirical claim about mechanism. Second, it
is in tension with the paper's own criterion: if additional compute reliably
improves closure, the modulation profile *is* resource-sensitive, whatever the
underlying mechanism. D3 cannot both make resource-sensitivity the criterion and
then dismiss observed resource-sensitivity as mechanistically inauthentic
without a further argument about why the mechanism matters.

Note also that §1's scope note lists "instruction-tuned and RLHF-trained
variants" and conspicuously omits outcome-RL reasoning models. As written, the
paper either excludes the most closure-relevant systems from its scope — in
which case it dates on publication and a referee will say so — or includes them
and has not engaged them.

**Three routes, in order of strength.**

*Route 1 — dissociate closure gain from closure competence, with evidence.* The
strongest available line, and it has empirical support D3 is not using:
chain-of-thought traces are frequently unfaithful to the computation that
produced the answer. If reasoning traces improve accuracy while not being the
causal route to the answer, then the compute is buying something other than
inferential closure, and D3's dismissal is vindicated rather than asserted.
Turpin et al. on CoT unfaithfulness [?] is the citation; verify it, because it
is doing real work here.

*Route 2 — test RE, not accuracy.* D3's criterion is congruentiality, not
task performance. The question is not whether extra compute raises benchmark
accuracy but whether it makes assertion *invariant under logically equivalent
paraphrase*. Those are different measurements and, as far as I know, the second
is not well studied. That is an opportunity: state it as the discriminating
experiment the framework predicts, and say what each outcome would mean. A paper
that names its own falsifying experiment reads as confident, and this one is
cheap to describe even if you do not run it.

*Route 3 — narrow the scope explicitly and own the cost.* Restrict to
non-reasoning autoregressive models, say why, and note that the framework
predicts outcome-RL training is the right direction for approximating normality —
which §1's scope note already licenses ("the framework tells us precisely what
such an architecture would have to achieve"). Weakest of the three, but honest,
and better than the current silence.

I would take Route 2 as the frame and Route 1 as the supporting evidence. Either
way this needs a subsection of its own in §8.2, not a clause.

---

## 4. Biggest philosophical gap: no engagement with internal representations

The bridge premise, from §3.1:

> For M, I shall argue, there is no p-invariant attitude for the prompt to
> elicit.

Everything hangs on this. It is what licenses `a fortiori` in §4.4 and what
makes §7.2's "sub-doxastic all the way up" more than a metaphor. And it is never
argued. §3.7 shows that `O_M` is p-sensitive — but p-sensitivity of the *output*
is consistent with a p-invariant state read out by a p-sensitive mechanism. That
is not a contrived alternative; it is the going hypothesis in a substantial
literature:

- **Burns et al. 2023 [H]** (ICLR), *Discovering Latent Knowledge in Language
  Models Without Supervision* — unsupervised identification of truth-like
  directions in activation space.
- **Azaria & Mitchell 2023 [H]** (EMNLP Findings), *The Internal State of an LLM
  Knows When It's Lying.*
- **Marks & Tegmark [?]**, *The Geometry of Truth.*

If a linear direction in activation space tracks truth across paraphrases, then
there is a p-invariant state, and D3's premise is false as stated. A referee
from philosophy of AI will know this literature. Its absence from a paper whose
central premise it contradicts is the most damaging omission in the manuscript.

**The fix makes the paper better.** Do not deny the representations. Weaken the
premise to what you actually need and what the evidence supports:

> There is no p-invariant state that *governs assertion*.

This is weaker, defensible, and more interesting. And the adversary's own best
result establishes it: Azaria & Mitchell find that the internal state registers
the falsity of what the model is *nonetheless asserting*. That is a
**dissociation between representation and assertion**. A state that tracks truth
but does not govern the assertion mechanism is precisely a state that fails the
constitutive-aim condition on belief — and D3 already has the vocabulary for
this in §7.2's monadic core.

The restructured claim then reads: LLMs may well have p-invariant
truth-correlated representations; what they lack is any mechanism making
assertion *accountable* to them. Hence assertion is not the expression of an
attitude, which is what §4.4 needs. You lose the strong "nothing behind the
output" claim and gain an argument that survives contact with the interpretability
literature — and one that explains why grounding (§5.1) is the right notion,
since `G_M`'s counterfactual-sensitivity condition is exactly a demand for that
missing accountability.

Then blunt the strong reading of the probing results with the critical
literature, both verified:

- **Levinstein & Herrmann 2024 [V]** (*Philosophical Studies*), *Still no lie
  detector for language models: probing empirical and conceptual roadblocks.*
  Your closest ally.
- **Herrmann & Levinstein 2024 [V]** (*Minds and Machines*), *Standards for
  Belief Representations in LLMs.* Sets out criteria a belief-representation
  claim must meet. Engaging these criteria directly — meeting them or explicitly
  failing them — is the single most credible move available in this section.

This wants roughly 800 words as a new §7.3 or a subsection of §8. Given the
budget pressure noted in §9, it should displace §3.2's N-failure (see §8.1).

---

## 5. Surviving problems, sharpened

### 5.1 The Gettier label is still wrong, and D3 has already found the right one

§6.3 says "The parallel is exact and worth drawing carefully," then draws a
parallel it describes as "transposed… one level down" in which "even the
justification is absent." A parallel that omits the relatum is not exact.
Gettier's contribution was that justification plus truth plus belief is
insufficient. Cell (ii) is `O_M φ ∧ ¬G_M φ ∧ φ` — ungrounded, hence unjustified,
hence the *pre*-Gettier case: true belief without justification, which is what
justification was introduced to exclude. A formal epistemology referee will
catch this in the abstract.

**But D3 has independently produced the correct analysis** and attached the
wrong name to it. This passage is exactly right:

> Had the world been different — had the population been 3,400 — the very same
> process would have produced the very same output. An assertion with that modal
> profile is defective as an assertion regardless of its truth value.

That is a **safety failure**, in the established sense: the output does not
track the fact across nearby possibilities. The literature and vocabulary
already exist:

- **Sosa 1999 [V]** (*Noûs*), "How to Defeat Opposition to Moore" — the safety
  condition.
- **Pritchard 2005 [H]**, *Epistemic Luck* — veritic luck, which is the precise
  name for cell (ii).
- **Nozick 1981 [H]** and **Dretske 1971 [H]** for the tracking/sensitivity
  ancestry.

Relabel cell (ii) as veritic luck or a safety failure, keep the modal-profile
argument verbatim, and cite Gettier 1963 [H] as family resemblance rather than
exact parallel. The term "veridical hallucination" is good and should stay.

**And the free upgrade the companion review flagged is still on the table.** §6.3
ends by noting that "a user who receives a cell-(ii) output and verifies it
acquires no reason to trust the system's next output." That user is where the
genuine Gettier structure lives: fluency functions as apparent justification, the
content is true, and the user forms a justified true belief that is not
knowledge, because the justification is disconnected from the truth-maker. That
is a textbook Gettier case, it is *generated by* cell (ii), and it explains why
cell (ii) is harmful rather than merely misclassified. It also connects directly
to D1's "unlicensed testimony" framing and to the testimony literature —
Lackey 2008 [H], Goldberg 2010 [H]. Two paragraphs, and you get to keep the
Gettier apparatus while putting it where it belongs.

### 5.2 `G_M`'s counterfactual condition is right, and largely unmeasurable

§5.1's second conjunct is the improvement: not just that E supports φ, but that
"had E not supported phi, the assertion mechanism… would not have produced it."
This defeats the triviality objection — that if E is the training corpus then
almost everything is grounded — because the counterfactual, not the mere
presence of supporting text, does the work. Good.

The cost is measurability, and it is unevenly distributed:

- **Retrieval-grounded assertions: tractable.** Ablate or contradict the
  retrieved context and observe whether the assertion survives. D3's §6.4 names
  this ("does the assertion persist when the putatively supporting evidence is
  ablated or contradicted in context?"). This works.
- **Parametric assertions: largely intractable.** "Had E not supported φ" ranges
  over counterfactual *training corpora*. Evaluating it properly means
  retraining. Training-data attribution methods approximate it, but at
  frontier scale they are an open research problem with known reliability
  limits, and §5.1's claim that interpretability methods "are, in effect,
  attempts to operationalise this conjunct" is true but should not be read as
  saying the problem is solved.

So `P(¬G_M φ | O_M φ)` is estimable for retrieval-grounded output and mostly not
for parametric output. State this. It is not a weakness of the definition — it
is a substantive finding, and it is the strongest possible argument for the
architecture D1 gestures at and Paper 2 develops: **the hallucination rate
becomes measurable only for systems built to make E explicit and citable at
inference time.** An unaugmented model's hallucination rate is well-defined and
practically unknowable. That sentence does more work for the trilogy than
anything else in D3.

**A unification D3 is missing.** `G_M`'s counterfactual-sensitivity condition and
§6.3's modal-profile diagnosis of cell (ii) are the *same condition* applied
twice — a sensitivity requirement linking the assertion event to the fact or to
the evidence. D3 presents them as separate insights in separate sections.
Stating the unification tightens the account considerably: hallucination is
assertion that fails a sensitivity condition on E, and veridical hallucination is
the case where that failure is masked by luck. One idea, two cells, and it
connects to the tracking literature cited in §5.1 above.

### 5.3 Signature (i) is the weakest empirical link in §8.2

§8.2 claims that on exposure of a framing effect, human subjects "characteristically
feel the pull of correction, revise, and often exhibit lasting transfer: having
seen through the survival/mortality frame, they resist it in new cases."

The debiasing literature does not straightforwardly support "lasting transfer."
Framing effects are notably robust, and durable cross-domain transfer from
single-shot debiasing is contested at best. This claim is stated without
citation, and it is one of the three pillars holding up the paper (§2).

Options: cite specific debiasing-transfer results if they support you; or retreat
to the weaker and better-supported claim, which is enough for the argument. The
weaker claim is about **normative acknowledgement**, not behavioural repair:
human subjects, shown the equivalence, *recognise the earlier response as an
error by their own standard*, whether or not they reliably resist the effect
next time. That is what establishes a standing internal standard, and it is what
§8.2 actually needs — "the violation is experienced as error relative to their
own standard" is the load-bearing sentence, and it does not require transfer.
LLMs have no analogue of the acknowledgement either, so the contrast survives
intact and stops depending on a contested empirical claim.

### 5.4 T-failure and the shape of the contribution

Repeating from §1 because it affects framing rather than content. T is now
defined in §2 but never shown to fail, never in the §3.8 table, and never used.
Yet T-failure settles the epistemic half of the thesis by itself: models assert
falsehoods, so `O_M φ → φ` is invalid, so `O_M` is not a knowledge operator, and
no modal machinery is needed.

Say this in §1 and again in §4.4. It costs three sentences and it tells the
referee precisely what the apparatus is for: the doxastic claim, which is the
hard one and the real contribution. As written, a reader may wonder why seven
axiom failures are being marshalled to establish something T alone delivers.

---

## 6. New problems D3 introduces

### 6.1 §7.1 conflates inferential isolation with closure failure

§7.2 is the best-improved section in the paper. §7.1, which sets it up, contains
a substantive identification presented as an observation:

> Inferential integration is what the normal axioms formalise: K is integration
> by modus ponens; M by simplification; D by consistency maintenance; 4 and 5 by
> self-access; RE by the minimal requirement that integration operate on
> contents rather than formulations. The wholesale failure of these axioms is the
> formal signature of inferential isolation.

These are two different properties:

- **Encapsulation** (Stich's criterion): state S cannot interact with the
  agent's general belief set. A boundary condition.
- **Closure failure** (what the axioms formalise): the endorsed set is not
  closed under consequence. A discipline condition.

They are independent. A module can be internally closed but informationally
sealed — that is roughly the Fodorian picture, and it is closer to Stich's
parser than closure-failure is. Conversely a system can be radically
unencapsulated and wholly undisciplined: everything interacts with everything,
just not logically. That second description fits an LLM far better than
encapsulation does. Content in a forward pass is globally available to the
computation; there is no wall.

So §7.1's claim that LLM states fit the isolation mark "strikingly well — indeed
better than Stich's own examples" is, on Stich's actual criterion, closer to
backwards. What LLMs exhibit is inferential *promiscuity without discipline*,
not isolation.

This matters more than it looks, because §7.1 is what earns the Stich label
before §7.2 qualifies it. If the isolation claim goes, the label is resting only
on the monadic core, which is exactly what §7.2 says survives. **The clean
repair is to let §7.2 do all the work:** drop the claim that LLMs satisfy Stich's
isolation criterion, say instead that they fail it in an unexpected direction —
too much interaction, no discipline — and present the monadic reconstruction as
the reason the term still earns its place. That is more honest, it strengthens
the novelty claim in §7.2 (the category needs reconstruction on *both* prongs,
not just the relational one), and it removes a claim a referee who knows Stich
will challenge.

### 6.2 The M-failure demonstration does not establish fixed-p failure

§3.3 explicitly and correctly defends K-failure under fixed p: "within one
context, with all premises explicit, high-confidence detachment failures occur."
§3.5 does the same for D: "D also fails within a fixed context."

§3.4 does not. Its example is a conjunction asserted in a summary and a conjunct
denied "under isolated query" — that is, under a *different* prompt-form. So the
M-failure demonstration is a p-sensitivity phenomenon, which means it is not
independent evidence; it is the RE-failure of §3.7 appearing earlier in the
paper under another name.

§3.8's "single root" paragraph half-concedes this, but the summary table presents
seven co-equal failures, which overstates the independence of the evidence. Two
fixes, and you want both: give M a fixed-p demonstration if one exists, and add
a sentence to §3.8 stating explicitly which failures are established under fixed
p (K, D) and which depend on varying p (M, and 4/5 in part). A referee who works
out that half the table reduces to one phenomenon will trust the rest less; a
paper that says so first looks rigorous.

Related, and worth a footnote to pre-empt: a reader may object that M should hold
automatically for a probability threshold, since a marginal cannot be lower than
a joint. The reply is that the two assertions are elicited under different
prompt-forms, so no single distribution is being marginalised — which is exactly
the point above, and stating it converts an apparent gap into a demonstration
that the p-parameter is ineliminable.

---

## 7. Internal inconsistencies

### 7.1 The abstract contradicts §4.2 — fix this first

Abstract:

> The failure of RE… establishes hyperintensionality, ruling out Kripke
> semantics and requiring neighborhood models or impossible-worlds semantics.

§4.2:

> bare neighborhood semantics still validates RE, since neighborhoods are sets of
> truth sets, and equivalent formulas share a truth set. To model O_M's RE-failure,
> the framework must be hyperintensionalised.

The body is correct; the abstract carries D2's error. This is the most damaging
single line in the manuscript, because it is the line a referee reads first and
it is a technical error in the précis of the paper's main formal result. Suggested
repair:

> …ruling out Kripke semantics and requiring either hyperintensionalised
> neighborhood models — with neighborhoods over formulations rather than sets of
> worlds — or impossible-worlds semantics.

### 7.2 "Credence threshold" imports the attitude the paper denies

The abstract and §3.1 both call `t` a "credence threshold," while §3.1's gloss
correctly defines it over output probability. A credence is a graded doxastic
state; ascribing one to M concedes in the definition of the central operator
exactly what §4.4 concludes against. Rename to **assertion threshold** or
**output-probability threshold** throughout. Purely terminological, zero
argumentative cost, and it closes an easy line of attack.

### 7.3 M is labelled inconsistently

§2 glosses M as "monotonicity axiom, agglomeration direction." §7.1 glosses it as
"simplification." §7.1 is right: M is `X(φ∧ψ) → (Xφ ∧ Xψ)`, the simplification
direction; agglomeration is C, `(Xφ ∧ Xψ) → X(φ∧ψ)`. Fix §2. Also worth a
sentence noting that C is defined but no C-failure is claimed — a deliberate and
correct choice, since any threshold account fails C for lottery/preface reasons
(Makinson 1965 [H]), including threshold accounts of human belief. Saying so
pre-empts a referee who notices the omission and assumes it was an oversight.

---

## 8. Citation debt

D3's argument is empirical at every crux and currently cites almost no empirical
work. For a philosophy venue this is survivable but it is the largest remaining
vulnerability, because the claims are strong, specific, and checkable.

### 8.1 Uncited claims that need support

| Section | Claim | Suggested support |
|---|---|---|
| §3.2 | tautologies denied at high confidence under syntactic disguise | **Weakest claim in the paper.** Modern models handle simple propositional validity well. If you cannot cite it, cut N — §4 needs the words anyway |
| §3.3 | reversal-curse instance | **Berglund et al. 2024 [V]** (ICLR). Your single best citation; currently referenced only as "the literature" |
| §3.4 | conjunctive biographical example | needs a real instance or explicit marking as illustrative |
| §3.5 | sycophancy across prompt-forms | **Sharma et al. 2024 [V]** (ICLR), *Towards Understanding Sycophancy in Language Models* — verified, and exactly on point |
| §3.5 | self-contradiction within a generation | **Mündler et al. 2024 [H]** (ICLR); **Elazar et al. 2021 [H]** (*TACL*) |
| §3.6 | verbalised confidence correlates poorly with output distributions | **Kadavath et al. 2022 [H]** — note the title cuts against you and must be engaged; **Lin, Hilton & Evans 2022 [H]** |
| §3.7 | "the empirical record on paraphrase sensitivity is unequivocal" | **Sclar et al. 2024 [V]** (ICLR); **Webson & Pavlick 2022 [H]**; **Zhao et al. 2021 [H]**; **Lu et al. 2022 [H]** |
| §6.5 | why LLMs hallucinate, architecturally | **Kalai & Vempala 2024 [V]** (STOC) — a theorem that calibration forces hallucination for rare facts. Materially strengthens the section and is better than an appeal to the objective function |
| §6.4 | attribution audit as method | **Rashkin et al. 2023 [V]** (*Computational Linguistics*) — the AIS framework is your metric, already operationalised |
| §8.2 (i) | correction transfer in humans | contested; see §5.3 |
| §8.2 (ii) | LLM failures not resource-sensitive | see §3 — the biggest gap |

### 8.2 Prior art that must be acknowledged

The grounded × true grid is the NLP field's **faithfulness × factuality**
distinction. Grounded-false is unfaithful-to-nothing-but-faithful-to-a-bad-source;
the mapping is close enough that an ML-literate referee will say the 2×2 is known.

- **Maynez et al. 2020 [H]** (ACL), *On Faithfulness and Factuality in
  Abstractive Summarization* — origin of the split.
- **Ji et al. 2023 [H]** (*ACM Computing Surveys*) — establishes it as standard.

Cite these *first*, then claim the right novelty, which is not the distinction
but three things the NLP literature does not have: the **normative asymmetry**
(only `¬G` constitutes hallucination), the **modal analysis** of cell (ii), and
the argument that the distinction follows from a theory of warranted assertion
rather than from engineering convenience. Positioned that way the prior art
becomes corroboration.

Similarly, **Hicks, Humphries & Slater 2024 [V]** (*Ethics and Information
Technology*), "ChatGPT is bullshit," is your nearest competitor: `O_M φ ∧ ¬G_M φ`
is a formalisation of Frankfurt-bullshit — assertion without regard for truth.
The debate is live; **Humphries, Hicks & Slater 2026 [V]** (*Philosophy &
Technology*) is a reply to a critic. Engaging it positions you in a running
conversation. Ignoring it looks like unfamiliarity, and §6 is where a referee
will expect it.

---

## 9. Meta-analysis: three documents, one paper

### 9.1 They are not three drafts of the same thing

| | D1 (`main.tex`) | D2 (brief) | D3 (this) |
|---|---|---|---|
| Central claim | `Says_M ⇏ K_M`, `⇏ True`, `⇏ Warranted` | `O_M` non-normal, hyperintensional | same as D2, argued |
| Semantics | none | asserted, incorrectly | correct, with a choice defended |
| Bridge to "not doxastic" | absent | absent | §3.1, asserted not argued |
| **Contextual truth `True(p｜j,t,u,f)`** | **yes, developed** | no | **no** |
| **Illicit universalization** | **yes** | no | no |
| **Conversational omniscience** | **yes** | no | no |
| **World access, `TrainedOn ⇏ Supports`** | **yes** | no | partly, inside `G_M` |
| Hallucination taxonomy | no | sketched | developed |
| Grounding operator | `Supports(E,p,c)` | `G_M φ` | `G_M φ`, counterfactual |
| Stich | unmentioned | cited | reconstructed |
| Objections | none | listed | argued |
| **Institutional warrant terminus** | **yes** | no | no |
| Sets up Papers 2–3 | **yes** | no | no |
| Prose | drafted, uneven | brief | publication-grade |

D3 supersedes D2 outright — every D2 idea appears in D3, better executed. There
is no reason to keep D2 as a separate document.

**D1 is a different matter.** D3 is the better paper, but it is not a superset.
Four of D1's contributions are absent from D3, and one of them D3 actively needs.

### 9.2 The complementarity is specific: `G_M` has no context parameter

This is the sharpest thing in this review.

D1 indexes truth to jurisdiction, time, intended use, and facts:
`True(p | j,t,u,f)`, with `Supports(E,p,c)` and `Resolved(p,c)`. D3 has
`G_M φ` — formulation-relative but with **no context parameter at all** — and
bare `φ` for truth.

That gap has a concrete cost inside D3's own taxonomy. Consider an assertion
grounded in a source that was authoritative in 2019 and superseded in 2024.
Is it grounded? On D3's definition: yes — E supports it, and the mechanism is
counterfactually sensitive to E. So a stale-but-faithfully-transmitted claim
lands in cell (iii), "error," and gets attributed to a bad evidential base. But
the evidential base was not bad; it was *inapplicable at t*. That is a grounding
failure relative to context, not a source error, and it calls for a different
intervention — currency checks and effective-date resolution, not corpus
correction. D3's §6.2 rests its whole case on cells (iii) and (iv) requiring
different interventions; staleness is a third intervention class the taxonomy
cannot see.

Jurisdiction behaves the same way. A claim grounded in EU regulation is not
grounded *for a US decision*, and D3 has no way to say so.

**So the two documents need each other in a statable way.** D3 supplies the
formal apparatus D1's non-entailment chains lack. D1 supplies the context
indexing without which D3's central operator cannot express the failure modes
that matter in the regulated domains this research programme is about. Indexing
`G_M` — `G_M(φ | c)`, with `c = (j,t,u,f)` per D1's notation — fixes it, and it
is a small change to make now while there is one paper to change.

It also repairs the trilogy's spine. `G_M(φ | c)` is one indexing step away from
Paper 2's `Supports(E,p,c)`, which makes the Paper 1 → Paper 2 transition a
genuine formal progression rather than a change of subject.

### 9.3 Recommended consolidation

Replace D1's §§Non-Entailment Thesis and Language Models as Sub-doxastic Systems
with D3, and keep four things from D1.

**Proposed Paper 1:**

| Section | Source |
|---|---|
| 1. Introduction | D3 §1 |
| 2. Formal preliminaries | D3 §2, with §7.3's M fix and the C footnote |
| 3. The output operator and axiom failures | D3 §3, minus N (§8.1), with fixed-p status marked (§6.2) |
| 4. Hyperintensionality | D3 §4, abstract corrected (§7.1) |
| 5. **Contextual truth and illicit universalization** | **D1** — and it now motivates indexing `G_M` |
| 6. Grounding and the 2×2 | D3 §5, with `G_M(φ｜c)` (§9.2) |
| 7. Hallucination as unwarranted assertion | D3 §6, cell (ii) relabelled (§5.1) |
| 8. **World access and the limits of training exposure** | **D1** — `TrainedOn ⇏ Supports/Current/Traceable`; independently motivates `G_M`'s counterfactual conjunct |
| 9. Sub-doxastic systems | D3 §7, §7.1 repaired (§6.1) |
| 10. Internal representations | **new** (§4) — the largest addition |
| 11. Objections | D3 §8, with the reasoning-model subsection (§3) |
| 12. **From model output to institutional warrant** | **D1**, kept short — the bridge to Paper 2 |
| 13. Conclusion | D3 §10 (not yet seen) |

**On D1's §Conversational Omniscience:** it is good material and it does not fit.
Its meta-knowledge condition, `¬K_A(p) → K_A(¬K_A(p))`, is axiom 5, which D3 §3.6
already covers formally. Either fold the substance into §3.6 as the philosophical
gloss on 5-failure — which is the economical move and strengthens a thin
subsection — or hold it for Paper 3, where bounded computation is the topic.

**Budget.** D3 as received runs perhaps 8,500 words through §8.2, so the full
manuscript is likely near 11,000. Adding §10 (~800) and the reasoning-model
subsection (~600), then importing three D1 sections, puts this well past 13,000.
Something must go. My recommendations, in order: cut N entirely (§8.1); compress
§4.3's impossible-worlds discussion, since §4.3 already concedes the frameworks
are intertranslatable and the choice is presentational; hold conversational
omniscience for Paper 3; keep D1's institutional-warrant section to a single
page. That lands near 11,000, which *Synthese* and *Philosophy & Technology* will
take.

**Do not** cut §8.2 or the new §10. Those are where the paper is won.

### 9.4 One risk in consolidating

Importing D1's institutional-warrant material pulls Paper 1 toward Paper 2's
subject, which your own scope rules forbid. The line I would hold: Paper 1 may
*define* `Warranted_{I,c}(p)` and state the output-to-warrant pipeline as a
terminus, because both are epistemological. It should not develop reliance
tiers, the components of warrant, governance, or the domain-bounded/frontier
comparison. And D3's §9 — which I have not seen — is the section most likely to
cross that line. When you send it, that is what I will be checking.

---

## 10. What to do next, in order

1. **Fix the abstract's neighbourhood-semantics claim** (§7.1). One sentence,
   and it is currently a technical error in the first paragraph a referee reads.
2. **Rename "credence threshold"** (§7.2). Terminological, free.
3. **Weaken the bridge premise** to "no p-invariant state that governs
   assertion," and **write the internal-representations section** (§4). The
   largest single improvement available, and it turns the strongest counter-evidence
   into support.
4. **Write the reasoning-model subsection** in §8.2 (§3). Route 2 as the frame,
   Route 1 as evidence.
5. **Relabel cell (ii)** as veritic luck / safety failure, keep the modal
   argument, and **add the recipient-Gettier paragraph** (§5.1).
6. **Index `G_M` to context** (§9.2), and decide the consolidation question in
   §9.3 before writing further, since it determines what else gets written.
7. **Retreat signature (i)** to normative acknowledgement (§5.3).
8. **Repair §7.1's isolation claim** (§6.1) and **mark fixed-p status** in §3.8
   (§6.2).
9. **Discharge the citation debt** (§8), and position against faithfulness/factuality
   and against Hicks et al. before a referee does it for you.
10. **Send §§8.3, 9, 10** so I can check the Paper 2 boundary and the conclusion's
    fidelity to what the paper argues.

---

## 11. Bibliography addendum

New items this review adds, beyond those in
`EVALUATION-modal-operator-manuscript.md`.

### Safety, luck, and tracking — for the cell (ii) relabel

- **[V]** Sosa, Ernest. "How to Defeat Opposition to Moore." *Noûs*, 1999. —
  The safety condition.
- **[V]** Montague, Richard. "Universal grammar." *Theoria*, 1970. — One of the
  two founding neighbourhood-semantics papers D3 §4.2 cites; the other is Scott
  1970 [H].
- **[H]** Pritchard, Duncan. *Epistemic Luck*. Oxford, 2005. — Veritic luck; the
  precise name for cell (ii).
- **[H]** Gettier, Edmund. "Is Justified True Belief Knowledge?" *Analysis*,
  1963. — Crossref returned only anthology reprints; the original is *Analysis*
  23(6). Cite as family resemblance, not exact parallel.
- **[H]** Nozick, Robert. *Philosophical Explanations*. Harvard, 1981. —
  Tracking/sensitivity ancestry.
- **[H]** Dretske, Fred. "Conclusive Reasons." *Australasian Journal of
  Philosophy*, 1971.
- **[H]** Priest, Graham. *Towards Non-Being: The Logic and Metaphysics of
  Intentionality*. Oxford, 2005. — Cited in D3 §2; confirmed indirectly via
  reviews in *Philosophical Books* 2007 and *Bulletin of Symbolic Logic* 2008.

### Empirical support for §3

- **[V]** Sharma, Mrinank, Meg Tong, Tomasz Korbak, et al. "Towards
  Understanding Sycophancy in Language Models." ICLR 2024. — Directly supports
  §3.5's D-failure claim, currently uncited.
- **[?]** Turpin et al. "Language Models Don't Always Say What They Think."
  NeurIPS 2023. — I could not confirm this via DBLP under that phrasing. It is
  load-bearing for Route 1 in §3, so verify it before relying on it.
- **[?]** A citable anchor for inference-time-compute scaling and outcome-RL
  reasoning models. I have not verified one and will not guess at a title. This
  is the citation §3 most needs; find it before writing that subsection.
- **[?]** Debiasing-transfer results, for §8.2 signature (i). If you cannot find
  support, take the retreat in §5.3 rather than citing loosely.

Everything else needed here — Berglund, Sclar, Kalai & Vempala, Rashkin,
Levinstein & Herrmann, Herrmann & Levinstein, Hicks et al., Maynez, Ji,
Kadavath, Burns, Azaria & Mitchell, Stich, Chellas, Nolan, Fagin & Halpern,
Shah & Velleman, Williamson, Brandom, Dennett, Tversky & Kahneman, Makinson —
is in the companion file with markers already attached.

---

# Addendum: §§8.3, 9, 10 and the reference list

The remainder of D3 arrived after the review above was written. The manuscript is
now complete in front of me. Nothing above needs retracting, but three items
change materially — one of them substantially in the paper's favour — and the
reference list turns out to be the most consequential single finding in this
review.

## A1. Headline: §9 already contains the answer to §3's objection

The largest gap I identified was the reasoning-model problem: §8.2 makes
resource-sensitivity the criterion distinguishing human competence-with-lapses
from LLM non-competence, names it as a falsifier, and then dismisses observed
resource-sensitivity in a single clause.

**§9's third directive is the answer, filed in the wrong section.** It proposes:

> The same battery applied across training checkpoints would show whether scale
> and post-training move systems toward normality or merely broaden the set of
> formulations handled, which the framework predicts and which matters for
> forecasting.

That is precisely Route 2 from §3 above — the discriminating experiment — stated
in the paper's own words. The distinction it draws is exactly the one §8.2 needs:
*movement toward normality* (RE-conformity generalising to unseen formulations)
versus *broader coverage* (more formulations handled, classicality unimproved).
Applied to reasoning models, the question becomes whether extra inference-time
compute makes assertion invariant under logically equivalent paraphrase, or
merely raises accuracy on formulations the training distribution already favours.

So the fix is much cheaper than I estimated. Rather than writing a new
subsection, forward-reference §9's directive 3 from §8.2 and make three moves
explicit:

1. State that the criterion is RE-stability, not task accuracy. §8.2 currently
   invites the reasoning-model objection by talking about closure *performance*;
   §9 already knows the right measurement.
2. Say what each outcome would mean — a system whose paraphrase-divergence falls
   with compute is moving toward classicality and the framework says so; a system
   whose accuracy rises while divergence holds is broadening coverage.
3. Note that §1's scope note already licenses the first outcome ("the framework
   tells us precisely what such an architecture would have to achieve").

That converts the weakest passage in §8.2 from a dismissal into a stated
prediction with a named test, which is a considerably stronger position. Route 1
(chain-of-thought unfaithfulness as evidence that compute buys something other
than closure) remains worth adding as support, but it is no longer load-bearing.

I would still add a sentence naming outcome-RL reasoning models, since §1's scope
list omits them and a referee will notice the omission before they reach §9.

## A2. §8.3 is well built, but part one undercuts the title

The two-part structure is right, and the diagnosis in part two is the good part:
"belief" is not a bare classifier but "a node in an inferential and practical
web," and extending it licenses a bundle — stability under paraphrase,
consistency, introspective self-reports, truth-conditional scoring, reliability
extrapolation, subject-presupposing appraisal — every element of which misfires.
"Terminological choices are cheap only when the inferential freight is cheap" is
the right formulation of the point.

Three problems.

**Part one concedes the title.** It says the substantive results are
terminology-independent and offers to restate them without "belief": "Call the
states 'beliefs*' if you like; the formal and methodological claims stand." But
the paper is titled *Assertion Without Belief* and its abstract leads with "LLMs
are neither epistemic nor doxastic systems." If everything substantive survives
dropping the word, the headline thesis is not among the substantive results — and
a referee can quote part one back as evidence that the doxastic claim is
decorative.

The repair is a reordering, not a retreat. What is terminology-independent is the
**formal and methodological** work: hyperintensionality, the semantic
consequence, the grounding/truth cross-cut, the measurement claim. The doxastic
claim is a *further* substantive claim — about which normality assumptions the
label licenses — and part two defends exactly that. So say: the formal results
stand regardless of labelling; the terminological question is *additionally*
substantive because of the inferential freight; hence the negative thesis is not
lexicographical. As written the section reads as concession followed by rescue;
reordered, it reads as a claim with two independent supports.

**The cluster-concept variant goes unanswered.** The objection offers three
versions — polysemy, cluster concept, stipulative precisification. Part one
answers stipulation and part two answers the freight worry, but the cluster
version is the strongest and is untouched: perhaps "belief" is a cluster concept
and LLM states satisfy enough of the cluster, no normality required.

The paper already has the answer and does not deploy it. §7.2's monadic core —
informational, behaviour-guiding, closure-free — *is* a cluster-concept response:
it concedes the peripheral cluster members and denies the core ones, on the
ground that the closure-related properties are what the formal tradition
idealises and therefore what individuates the concept. Two sentences connecting
§8.3 to §7.2 closes this, and the connection strengthens both sections.

**The RAM disanalogy is weaker than claimed.** "Where 'memory' for RAM exported a
harmless functional abstraction" — the storage-and-retrieval metaphor for human
memory has been criticised in cognitive science for exporting exactly the wrong
model, so the contrast case is contested. The argument does not need it; either
hedge ("arguably harmless") or choose a cleaner example. A philosophy-of-science
referee will pick at this precisely because it is offered as the clear case.

## A3. §9 is strong, does not cross into Paper 2, and contains the paper's most citable contribution

I flagged in §9.4 above that §9 was the section most likely to breach the
Paper 1/Paper 2 boundary. It largely does not. There are no reliance tiers, no
institutional authorization, no governance architecture, no domain-bounded versus
frontier comparison. It is evaluation methodology, which is properly Paper 1's to
state as a consequence. The two forward-looking gestures — "regulatory language
that asks whether a system 'knew' a fact" in §8.3, and "downstream reliance" in
directive 4 — are the right length for hooks. **The boundary risk did not
materialise.**

**Directive 2 is the most actionable and most citable thing in the paper:**

> evaluation suites should include items whose correct answers are absent from
> the accessible evidence, where the only correct behaviour is abstention, and
> should penalise true answers on such items.

That is a concrete, implementable, and as far as I know novel benchmark design
proposal, and it follows deductively from the cell-(ii) argument. It will be
cited by people who never engage the modal logic. Consider promoting it: name it
in the abstract, which currently mentions the measurement target but not this.

**Directive 3's "scalar summary of the distance between the system's operator and
a proposition-directed one"** is also a good idea and gives the framework an
empirical handle. Two caveats to add: matching confidence across paraphrases is
itself non-trivial, since the confidence proxy is formulation-sensitive too; and
the equivalence battery needs to control for the possibility that paraphrases
differ in tokenisation difficulty rather than logical form.

**The constructive closing is the right way to end the section** — four
architectural requirements, and "turns 'do LLMs believe?' from a slogan-war into
a checklist" earns its place.

Two substantive problems, both versions of the measurability asymmetry from §5.2.

**Directive 1 overstates the estimators.** "Training-data attribution methods,
causal tracing, and retrieval-attribution checks are imperfect but improving
estimators of exactly this relation." The retrieval case and the parametric case
differ in kind, not degree. Ablating or contradicting a retrieved context is a
direct test of the counterfactual. Evaluating "had E not supported φ" for
parametric knowledge ranges over counterfactual training corpora; TDA
approximates this, but at frontier scale with known and substantial reliability
limits. "Imperfect but improving" flattens that distinction. State the asymmetry:
`P(¬G_M φ | O_M φ)` is estimable for retrieval-grounded assertions and largely
not for parametric ones.

**Directive 2 inherits the same limit and does not say so.** Penalising true
answers on items "whose correct answers are absent from the accessible evidence"
requires establishing that absence. For a constructed evaluation where you
control the retrieval corpus, straightforward. For a frontier model's parametric
knowledge, you cannot verify that the corpus fails to support a claim. So the
directive is implementable for retrieval-controlled setups and not, in general,
for parametric ones. One sentence fixes it.

Both points push the same way, and it is the way the trilogy wants to go: the
hallucination rate becomes measurable only for systems built to make E explicit
and citable at inference time. That is the strongest bridge from this paper to
Paper 2, it is now supported twice over, and it is currently unstated.

**Directive 4 slightly overreaches.** "Retire subject-level appraisal from
technical evaluation" is right about "knows" and "believes," but "honest" and
"deceptive" have operational definitions in safety evaluation that do real work.
Better: reformulate where the doxastic idiom smuggles in normality assumptions,
and note that the safety-relevant notion — divergence between what a model's
internal state registers and what it asserts — is expressible in the
`(O_M, G_M)` idiom without loss. That framing also gives you a natural hook into
the internal-representations section recommended in §4 above, where the same
dissociation is the central evidence.

## A4. §10 is accurate to the paper — and repeats the abstract's error

Tested against the body, the conclusion is faithful: the axiom list matches §3,
the Stich qualification matches §7.2 and is stated rather than elided, the
hallucination definitions match §6. That is the main thing a conclusion has to
get right, and it does.

But it reproduces the technical error from the abstract:

> their assertion behaviour requires neighborhood or impossible-worlds semantics

§4.2 established that bare neighbourhood semantics validates RE and therefore
cannot model an RE-failing operator without hyperintensionalisation. So the error
now appears in **both the abstract and the conclusion** — the first and last
things a referee reads, with the correct statement buried in §4.2 between them.
This raises §7.1 above from "fix this first" to the single most urgent edit in
the manuscript. Both instances need the qualifier: *hyperintensionalised*
neighbourhood models, with neighbourhoods over formulations.

Two smaller notes. "Gettier-style" carries the mislabel from §5.1; if you adopt
the veritic-luck relabel, the conclusion changes too. And "useful without being
trustworthy in the way believers are" introduces trustworthiness in the final
sentence, a notion the paper has not argued about and which has its own
literature. It reads well; it is also the one clause in §10 that outruns the
body. Either drop "trustworthy" for something the paper earned — "reliable in the
way believers are" is closer to §8.2's economy argument — or accept it as
rhetorical closure knowing a careful referee may query it.

## A5. The reference list is the most consequential finding in this review

Fourteen references. Every one is philosophy or modal logic. **There is not a
single empirical citation in the manuscript.**

No Berglund on the reversal curse, which §3.3 invokes by name as "the reversal
curse literature." No Sclar on formatting sensitivity, though §3.7 asserts "the
empirical record on paraphrase sensitivity is unequivocal." No Sharma on
sycophancy, though §3.5 rests its D-failure on "sycophancy results." No Kadavath
or Lin on verbalised confidence, though §3.6 rests 4-failure on "the calibration
literature." No Rashkin, though §9's central directive is attribution audit. No
Kalai & Vempala, though §6.5 argues hallucination is architectural. No Maynez or
Ji, so the faithfulness/factuality prior art is neither cited nor distinguished.
No Hicks et al., so the nearest competitor is absent. No Levinstein & Herrmann,
so the literature that contests §3.1's bridge premise is absent.

The first thesis is an empirical claim about the behaviour of real systems,
supported by seven subsections of behavioural description, none of which cites
anything. §3 is the foundation of the paper and it currently rests on the
author's assurance. A referee who reaches §3.7's "unequivocal" with no footnote
will discount the entire section, and they will be right to.

This is also the most fixable problem in the manuscript. The companion file
`EVALUATION-modal-operator-manuscript.md` §7 and §8.1 above list what goes where,
with reliability markers attached. Roughly twenty additions, most of them
verified, and §3 goes from assertion to evidence.

A second compositional gap: no Williamson, Brandom, or Shah & Velleman, so
§7.2's monadic core — informational, behaviour-guiding, closure-free — is built
without the constitutive-aim-of-belief and norm-of-assertion literature that
would ground it. §8.2's practical-reasoning-economy argument is *reinventing*
that literature, well, and unattributed. Adding it costs three citations and
converts a good original argument into a good argument with a pedigree, which is
strictly better in review. Same for §6.3: no Sosa or Pritchard, so the
modal-profile argument reinvents the safety condition.

### Corrections and checks to the list as given

| Entry | Status |
|---|---|
| Fagin & Halpern (1988), *AI* 34(1), 39–76 | **Check the year.** Crossref returns **1987** for "Belief, awareness, and limited reasoning" in *Artificial Intelligence*. Volume 34(1):39–76 is consistent with either, since AIJ 34(1) straddles the year boundary. Pick one and be consistent; 1987 is what the indexed record says |
| Stich (1978), *Philosophy of Science* 45(4), 499–518 | **[V]** venue and year verified; volume and pages plausible |
| Montague (1970), *Theoria* 36(3), 373–398 | **[V]** title, venue, year verified |
| Nolan (1997), *Notre Dame Journal of Formal Logic* 38(4), 535–572 | **[V]** title, author, venue, year verified |
| Priest (2005), *Towards Non-Being*, OUP | **[V]** confirmed indirectly via reviews in *Philosophical Books* (2007) and *Bulletin of Symbolic Logic* (2008) |
| Rantala (1982), *Acta Philosophica Fennica* 35, 106–115 | **[?]** I could not confirm this. *Acta Philosophica Fennica* is poorly indexed in both Crossref and DBLP. Your page numbers suggest you have the source in hand; if so, fine — but verify against the physical record rather than a secondary citation, since this entry is doing real work in §§2 and 4.3 |
| Gettier (1963), *Analysis* 23(6), 121–123 | **[H]** standard and matches my recollection; Crossref returned only anthology reprints |
| Tversky & Kahneman (1981), *Science* 211(4481), 453–458 | **[H]** standard, details look right |
| Scott (1970), in Lambert (ed.), *Philosophical Problems in Logic*, Reidel, 143–173 | **[H]** standard citation |
| Chellas (1980), Berto & Jago (2019), Dennett (1987), Hintikka (1962) | **[H]** standard; no concerns |

One absence worth noting: **Sosa (1999)** and **Pritchard (2005)** are needed for
§6.3 under the relabel, and **Lenzen (1978)** or equivalent for the S4-versus-S5
point, since §2 asserts the S5 idealisation without acknowledging that axiom 5 for
knowledge is widely rejected. §2 does hedge ("sometimes weakened to S4.2 or S4"),
which is better than D2 managed, but an unsupported S5 attribution in the
preliminaries of a formal epistemology paper still invites a footnote.

## A6. Revised verdict and consolidated next actions

The complete manuscript is better than the partial one suggested, and one of my
two headline gaps is substantially cheaper to close than I estimated. Revised
priority order, superseding §10 above:

1. **Fix the neighbourhood-semantics claim in the abstract *and* §10** (A4, §7.1).
   Two sentences. It is a technical error in the two places a referee reads first
   and last, contradicted by the paper's own §4.2.
2. **Populate the empirical bibliography** (A5). Twenty-odd citations, mostly
   verified, transforming §3 from assertion to evidence. Highest ratio of
   credibility gained to effort spent in the whole list.
3. **Forward-reference §9's directive 3 from §8.2** and name outcome-RL reasoning
   models in §1's scope note (A1). The paper already contains its own answer.
4. **Weaken the bridge premise** to "no p-invariant state that governs
   assertion," and write the internal-representations section (§4). Still the
   largest philosophical improvement available, and directive 4 (A3) now gives it
   a second point of attachment.
5. **Reorder §8.3** so part one does not appear to concede the title, and connect
   the cluster-concept variant to §7.2's monadic core (A2).
6. **Rename "credence threshold"** throughout (§7.2). Free.
7. **Relabel cell (ii)** as veritic luck / safety failure, keep the modal
   argument, add the recipient-Gettier paragraph, and propagate the relabel into
   §10 (§5.1, A4).
8. **State the measurability asymmetry** in §9 directives 1 and 2 (A3). It is the
   strongest available bridge to Paper 2 and is currently unstated despite being
   supported twice.
9. **Index `G_M` to context** and settle the consolidation question in §9.3
   before writing further.
10. **Retreat signature (i)** to normative acknowledgement (§5.3); **repair
    §7.1's isolation claim** (§6.1); **mark fixed-p status** in §3.8 (§6.2);
    **soften directive 4** (A3); add Sosa, Pritchard, Williamson, Brandom,
    Shah & Velleman, and a note on S5 (A5).

Items 1, 2, 3, and 6 are mechanical and could be done in a day. Items 4 and 5 are
the intellectual work. Item 9 is the decision that determines how much else gets
written.
