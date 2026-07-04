# Notation & Conventions

The formal material in this book shares one vocabulary of symbols and one set of
conventions. They are collected here for reference; each is also introduced in
context where it first appears.

## Conventions

- **Components** are named in full on first use — Enterprise Prompt Governance,
  Runtime Output Controls, Monitoring, Detection & Response — and abbreviated
  thereafter as **EPG**, **ROC**, and **MDR**.
- **References** point to chapters and sections as "Chapter 3" and "§3.2", and to
  the reference material as "Appendix A". A bare "§" refers to a section of the
  current chapter.
- **Control surfaces** are written `S_x` for the surface governing element `x`
  (for example `S_prompt`, `S_output`). A surface's governability class is
  `γ(S)`.
- **Governance components** are written `g` (or `g_EPG`, `g_ROC`, `g_MDR` for the
  specific instances), and `Φ(g)` is the guarantee a component provides.

## Symbols

| Symbol | Meaning |
|--------|---------|
| I | Set of all AI interactions |
| P, U, M, Θ, O, O' | Prompts, User inputs, Models, Inference configs, Outputs, Delivered outputs |
| T | Time domain |
| Σ | Set of all control surfaces |
| S_x | Control surface for element x |
| γ(S) | Governability class of surface S |
| G | Set of governance components |
| g = (S, C, E, A, R) | A governance component |
| Φ(g) | The guarantee provided by component g |
| K(g₁, g₂) | Interface contract between components |
| A_g(i, t) | Audit record from component g for interaction i at time t |
| chain(i) | Composed audit chain for interaction i |
| R(i) | Compliance risk for interaction i |
| R_Sₖ | Risk attributable to surface Sₖ |
| ⊕ | Component composition operator |
| O(φ), F(φ), P(φ) | Deontic modalities (Chapter 2): obligatory, forbidden, permitted |
| ⊨ | Satisfaction relation |
| ver(x, t) | Version of element x at time t |
| observe(e) | Observation function for element e (Axiom 4) |
| identity(x, t) | Versioned identity of element x at time t (Axiom 5) |
