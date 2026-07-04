# Preface

Enterprises are deploying AI faster than they can govern it. Prompts are written
ad hoc, outputs reach users unfiltered, and audit trails are incomplete. When a
regulator asks the only question that matters — *"show me exactly which rules
governed this AI interaction"* — most organizations have no answer.

This book is about building the answer. It presents **Clad**, a formally modeled
governance framework for AI in regulated industries. Clad does not promise that
an AI system will never misbehave; no framework honestly can. It promises
something a regulator and an internal auditor can actually use: **provability**.
Every governed interaction produces auditable evidence of which rules were in
effect, which evaluations ran or failed, and which model and configuration
versions took part.

## Who this book is for

It is written for the people who are accountable for AI in production — CIOs,
CISOs, senior AI and data architects, and governance and compliance leaders — and
for the engineers who build the controls they sign off on. It assumes you are
comfortable with the shape of enterprise systems and regulatory obligations. It
does not assume a background in formal methods: the mathematics is introduced as
it is needed, and every formal claim is stated in plain language first.

## How the book is organized

The argument builds in three parts.

**Part I — Foundations** develops the meta-framework: the axioms Clad rests on,
the five control surfaces of an AI interaction, the model of a governance
component, and the algebra by which components compose. Everything later in the
book is an instance of this foundation.

**Part II — The Three Control Layers** works through the three components that
sit in the interaction pipeline. Chapter 2 governs the prompt before it reaches
the model (Enterprise Prompt Governance, EPG). Chapter 3 governs the output
before it reaches the user (Runtime Output Controls, ROC). Chapter 4 watches the
whole system over time (Monitoring, Detection & Response, MDR).

**Part III — Assurance in Practice** turns the framework outward, mapping its
controls to the regulatory frameworks and industry standards that enterprises
must answer to.

The **appendices** hold the reference material: the consolidated formal model and
theorems, worked examples, templates and classifier specifications, and a
glossary of terms and notation. The formal model is complete enough to check the
book's central claims; the chapters draw on it without requiring you to read it
front to back.

## How to read it

Read Part I once, carefully — it defines the vocabulary the rest of the book
uses. Part II's three chapters are independent enough to read in any order, or to
consult one at a time when you are building that layer. Part III and the
appendices are reference material to return to.

Throughout, components are named in full on first use and then abbreviated (EPG,
ROC, MDR). Cross-references point to chapters and sections ("Chapter 3", "§3.2")
and to the appendices ("Appendix A"). The symbols used in the formal material are
collected in *Notation & Conventions*, which follows this preface.

## A note on validation status

Clad has been developed through formal design and multi-model adversarial review
across several rounds. It has **not** yet been validated through implementation or
empirical testing at enterprise scale. Its formal properties and guarantees are
architecturally sound but operationally unverified. Pilot deployment with
representative workloads is recommended before enterprise rollout. Where a
specific guarantee carries a specific caveat, the relevant chapter says so.
