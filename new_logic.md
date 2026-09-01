




The first step in trying to develop an epistemology of Large Language Models (LLM) is to clearly define how these terms are to be understood within this context. A brief review of the technical history of LLMs can be helpful here while evaluating different epistemic approaches and clearly identifying the strengths and weaknesses of the approach can set the boundaries for what this paper can and cannot explain.




. The Perceptron Era (1957)
•	The Formula:
\(\^{y}=H\left(\sum _{i=1}^{n}w_{i}x_{i}+b\right)=H(\mathbf{w}^{T}\mathbf{x}+b)\)
•	Where:
o	\(\mathbf{x}, \mathbf{w} \in \mathbb{R}^n\) represent the input feature and learnable weight vectors.
o	\(b \in \mathbb{R}\) is the scalar bias.
o	\(H(z)\) is the non-differentiable Heaviside step function returning \(1\) if \(z \ge 0\) and \(0\) otherwise.
2. Multilayer Perceptrons & Backpropagation (1986)
•	The Formula:
\(\mathbf{a}^{(l)}=\sigma \left(\mathbf{W}^{(l)}\mathbf{a}^{(l-1)}+\mathbf{b}^{(l)}\right)\)
•	Where:
o	\(\mathbf{a}^{(l)}\) is the activation vector of layer \(l\) (where \(\mathbf{a}^{(0)} = \mathbf{x}\)).
o	\(\mathbf{W}^{(l)}\) is the weight matrix mapping layer \(l-1\) to layer \(l\).
o	\(\sigma(z)\) is a continuous, differentiable non-linear activation function (e.g., \(\text{sigmoid}(z) = \frac{1}{1 + e^{-z}}\) or \(\tanh(z)\)) which allows the application of the chain rule via \(\frac{\partial \mathcal{L}}{\partial \mathbf{W}}\).
3a. Spatial Alignment: Convolutional Neural Networks (1998)
•	The Formula:
\(S(i,j)=(I*K)(i,j)=\sum _{m}\sum _{n}I(i-m,j-n)K(m,n)\)
•	Where:
o	\(I\) is the input 2D feature grid (or image tensor).
o	\(K\) is the learnable localized convolutional kernel (filter).
o	\(S(i,j)\) is the output feature map, enforcing strict translation invariance and weight sharing across spatial coordinates \((i,j)\).
3b. Temporal Alignment: Recurrent Neural Networks & LSTM (1997)
•	The Formula (Standard RNN Recurrence):
\(\mathbf{h}_{t}=\tanh \left(\mathbf{W}_{hh}\mathbf{h}_{t-1}+\mathbf{W}_{xh}\mathbf{x}_{t}+\mathbf{b}_{h}\right)\)
•	Where:
o	\(\mathbf{x}_{t}\) is the input vector at sequential time step \(t\).
o	\(\mathbf{h}_{t}\) is the hidden state acting as the sequential memory.
o	\(\mathbf{W}_{hh}\) and \(\mathbf{W}_{xh}\) are shared transition matrices across all time steps, forcing step-by-step computational dependency along the temporal axis.
•	Note on LSTM: Hochreiter & Schmidhuber expanded this by adding an internal cell state \(\mathbf{C}_{t}\) managed by an element-wise gating mechanism: \(\mathbf{f}_t = \sigma(\mathbf{W}_f[\mathbf{h}_{t-1}, \mathbf{x}_t])\), allowing gradients to flow back through time linearly via \(\mathbf{C}_t = \mathbf{f}_t \odot \mathbf{C}_{t-1} + \mathbf{i}_t \odot \mathbf{\tilde{C}}_t\).
4. The Transformer Revolution (2017)
•	The Formula (For reference):
\(\text{Attention}(Q,K,V)=\text{softmax}\left(\frac{QK^{T}}{\sqrt{d_{k}}}\right)V\)
•	Where:
o	\(Q, K, V\) are Query, Key, and Value matrices projected from the input token sequence.
o	\(d_{k}\) is the scaling factor (dimension of keys) to prevent vanishing gradients in the softmax layer.
5. GPT-3 and Autoregressive Scaling (2020)
•	The Formula (Causal Multi-Head Attention & Next-Token Objective):
\(\text{MaskedAttention}(Q,K,V)=\text{softmax}\left(\frac{QK^{T}}{\sqrt{d_{k}}}+M\right)V,\quad \text{where\ }M_{ij}=\begin{cases}0&i\ge j\\ -\infty &i<j\end{cases}\)
•	Where:
o	\(M\) is the causal attention mask matrix that forces upper-triangular logits to \(-\infty \). This structurally prevents token \(i\) from attending to future tokens \(j\), enabling the model to optimize the autoregressive language modeling objective:
\(\mathcal{L}=-\sum _{t}\log P(x_{t}\mid x_{<t};\Theta )\)
o	\(\Theta \) represents the 175 billion dense parameters optimized purely to predict the conditional probability of token \(x_{t}\) given all previous tokens \(x_{<t}\).









I like the clarity and correctness of coding. I take comfort in the fa t that all the errors are mine. I like Rust beause of how up front it is with me about how wrong I am. This means that I am more than just bothered by the hallucinations of Large Language Models; I take it as a personal affront. On one hand, I have an amazingly functional tool at my disposal. Nothing makes an engineer happier. On the other hand, the tool will lie to me. Code has never lied to me; I actually don't have words to express how this makes me feel. If I was good at using words to express how I feel, why would I be a software developer? At a core level, I wanted to make it stop.


Its pretty easy to unerstand why hallucinations happen when you consider the structure of a Large Language Model (LLM). In 2017, The "Attention Is All You Need" paper focuses on next-token prediction via entropy loss. An LLM learns by prediction the next word in a series, one token at a time. The model learns a probablility distribution over all possible next tokens using cross-entroy loss. Cross-entropy loss is basically a measuremnt of how wrong was my prediction?    

LLMs are next‑token predictors trained to match patterns in data, not to maintain a world model grounded in reality.
When input leaves the “manifold” of what they’ve reliably seen (out‑of‑distribution queries, ambiguous prompts, or missing information), they still must output tokens. The model will choose the most statistically “plausible” continuation, which can be wrong but fluent.
There’s no built‑in notion of “I don’t know” or “this contradicts the world”; that has to be layered on top via training and tooling.

1









Key symbols used:

∈ (element of)
→ (maps to)
→ (arrow): function / implication → f: X → Y
∪ (union)
⊥ (bottom / no answer)
∣ (conditional bar)


1. Basic Ontology



Let:

- W = set of possible world states.
- q ∈ Q = a question (query).
- a ∈ A = an answer (proposition or structured claim).
- E = evidence accessible to the system (documents, DB rows, tool outputs).
- M = the model (LLM + surrounding pipeline), with behavior  
  F_M: Q → A ∪ {⊥, H} as before.

We assume a truth predicate:

- True(a, w) = “answer a is true in world w”.

There is also an evidence relation:

- Supports(E, a) = “the accessible evidence E supports a”.

And a credence function (Bayesian / probabilistic aspect):

- Cr_M(a ∣ E) ∈ [0, 1] = “M’s rational degree of belief in a given E”.


2. LLM‑Style Belief and Assertion 

The system’s belief state for a question (q) (given evidence (E)) can be represented as a distribution over answers:
[
Bel_M(⋅ | q, E) = Cr_M(⋅ | q, E).
]

An assertion policy chooses a particular answer (a) (or abstention (⊥)):

[
Ans_M(q, E) =
  a*  if  Cr_M(a* | q, E) ≥ θ  and  Supports(E, a*)
⊥  otherwise
]

for some confidence threshold (θ ∈ (0,1)) and grounding condition (Supports(E, a*)).

This matches our low‑hallucination environment: don’t assert beyond evidence; abstain when credence is low or grounding is absent.


3. :

Knowledge as Reliable, Grounded, High‑Credence True Belief Define that the system knows an answer (a) to question (q) in world (w) if and only if:
Belief / assertion
(Ans_M(q, E) = a).
(The system actually asserts (a) rather than abstaining.)

Truth
(True(a, w)).
(Standard correspondence condition.)

Grounding
(Supports(E, a)).
(The belief is based on accessible evidence, not merely on statistical fluency.)

High credence
(Cr_M(a | E) ≥ θ).
(The system would, in principle, bet on (a) at favorable odds.)

Reliability (externalist)
The mapping ((q,E) ↦ a) produced by (M) is reliable in the relevant environment:
[
Rel(M, 𝒟) high
]
where (𝒟) is the distribution of ((q, w, E)) in this domain, and
[
Rel(M, 𝒟) := Pr_{(q,w,E) ∼ 𝒟}[True(Ans_M(q,E), w)].
]

Then:

[
K_M(q, a, w) (“M knows that (a) is the answer to (q) in (w)”)
]

is defined as the conjunction of (1)–(5).

This is a formal, LLM‑centric reliabilist epistemology:

It is externalist: knowledge depends on actual reliability relative to the environment, not just internal states.
It is Bayesian: credence (Cr_M) and threshold (θ) formalize degrees of belief.
It enforces anti‑hallucination: unsupported answers ((¬Supports(E,a))) cannot count as knowledge, and often will not be asserted.

4 Hallucination 
in This Model A hallucination is then:
Hallucination_M(q, a, w, E) ⇔
(Ans_M(q, E) = a) ∧ ¬True(a, w)

with one or both of:

(¬Supports(E, a)) (unsupported), or
(Cr_M(a | E) is high but Rel(M, 𝒟) is not) (miscalibrated belief).

The low‑hallucination regime is:

Pr_{(q,w,E) ∼ 𝒟}[Hallucination_M(q, Ans_M(q,E), w, E)] ≈ 0.



1. The Loss Function Scales With Domain Coherence
Our proposed loss:
L = w₁×L_CE + w₂×L_consistency + w₃×L_grounding + w₄×L_calibration
**This works when:**
- Grounding facts are unified → domain-specific corpora provide this
- Consistency is meaningful → domain ontologies provide this
- Calibration is learnable → domain has measurable ground truth

**This breaks when:**
- Grounding facts contradict → internet-scale data has this
- Consistency is ambiguous → cross-domain data has this
- Calibration is undefined → what accuracy metric applies to all tasks?

**Implication:** Smaller, domain-coherent models can implement this loss successfully. Large, diverse models can't.

### 2. The K(p) → p Constraint

From our earlier discussion:
K(p) → p requires:

p is true (grounded to reality)
p is justified (has epistemological warrant)
p is consistent (doesn't contradict other K(q))
text

