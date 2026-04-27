# Enterprise Prompt Governance: A Constraint-Based Framework for Regulated AI

**White Paper 1 — Clad**

**Version:** 1.0  
**Date:** April 2026  
**Audience:** CIO/CISO, Senior AI Architects, Compliance Officers  
**Relationship:** Builds on the Clad Meta-Framework (v5)  
**Scope:** Governs control surface S_prompt — the instructions sent to AI models

---

## 1. Executive Summary

### 1.1 The Problem: Unstructured AI Prompting in Regulated Industries

Generative AI adoption is accelerating across Fortune 500 enterprises. Healthcare systems deploy diagnostic assistants, financial institutions build investment research tools, and energy companies automate compliance documentation. Yet beneath this progress lies a fundamental governance gap: prompt engineering remains ad-hoc, unstructured, and ungoverned.

The current state is unsustainable. Teams across an enterprise build prompts independently, with no shared governance framework, no compliance review process, and no visibility into what instructions are actually being sent to AI models. A marketing team crafting customer service responses, a legal team building contract analysis tools, and an operations team automating incident reports operate in isolation. Each makes autonomous decisions about what an AI system should do, how it should behave, and what constraints it should respect.

This absence of prompt governance creates compounding failure modes that threaten both regulatory compliance and operational integrity:

**Compliance violations.** Without governed prompts, enterprises expose themselves to direct regulatory violations. A healthcare chatbot built without HIPAA-compliant prompt constraints may instruct the model to include protected health information (PHI) in responses. A financial advisory tool lacking SOX-aligned prompt boundaries may generate investment recommendations that constitute unauthorized financial advice. These are not hypothetical risks. They represent the predictable outcome of deploying AI systems where the instructions sent to models are neither reviewed nor constrained by compliance requirements.

**Inconsistency at scale.** When identical business questions receive wildly different quality answers depending on which team built the prompt, the enterprise loses operational coherence. One customer service team's prompt produces accurate, policy-aligned responses while another team's prompt for the same function generates contradictory answers. This inconsistency erodes trust in AI systems and creates liability when different parts of the organization make conflicting commitments to customers, regulators, or business partners.

**Shadow AI proliferation.** The absence of practical governance frameworks drives teams to build AI tools outside official channels. When the path to governed AI deployment is unclear or burdensome, teams bypass governance entirely. They stand up ungoverned chatbots, deploy unaudited analysis tools, and integrate AI capabilities that never touch the compliance review process. Each shadow AI system represents an unauditable risk vector. The enterprise has no inventory of what AI systems exist, no understanding of what instructions those systems operate under, and no mechanism to audit their behavior.

**The PoC-to-production gap.** Proof-of-concept AI projects routinely demonstrate compelling value, yet fail to graduate to production deployment. The cause is not technical infeasibility. It is governance infeasibility. Without a disciplined prompt governance framework, enterprises cannot answer the questions that gate production deployment: What constraints govern this system? How do we know those constraints are enforced? What happens when requirements change? Who approved these instructions? The absence of answers keeps valuable AI capabilities trapped in pilot purgatory.

These failure modes share a common root cause: enterprises treat prompts as code, not as governance artifacts. Prompts are instructions that determine AI system behavior, yet they receive none of the governance rigor applied to other behavior-determining artifacts like access control policies, data retention rules, or incident response procedures. The result is predictable: ungoverned instructions produce ungoverned behavior.

### 1.2 What EPG Provides

Enterprise Prompt Governance (EPG) is a constraint-based governance framework for managing AI prompts in regulated enterprises. It addresses the failure modes outlined above through a deliberate architectural choice: EPG defines boundaries within which teams build prompts. It does not author prompts.

This distinction is foundational. EPG is not a prompt dictation system. It does not maintain a central library of approved prompts. It does not require teams to select from pre-written templates. Instead, EPG establishes a constraint hierarchy that teams must satisfy when building prompts for their specific use cases. A healthcare department might inherit enterprise-wide constraints prohibiting PHI disclosure, add department-specific constraints requiring clinical disclaimer language, then allow individual project teams to craft prompts that satisfy both constraint sets while addressing their unique business requirements.

**Hierarchical constraint inheritance** implements this model. Constraints flow from enterprise governance teams to department leads to project teams. Each level adds specificity without removing protections established upstream. An enterprise-level constraint might require "no financial advice" across all AI systems. A finance department constraint might add "all numerical outputs must cite data sources." A trading desk project constraint might further specify "volatility calculations must reference the last 90 days of market data." The project team builds prompts that satisfy all three constraint levels, but retains full flexibility in how those prompts accomplish their specific business objective.

**Full audit trail** provides compliance teams with version-stamped, tamper-evident records of every prompt evaluation. When a regulator asks "what constraints governed this AI system on March 15, 2026," the enterprise produces a cryptographically verifiable record showing the exact constraints in effect, the prompt that was evaluated, the evaluation outcome, and the governance chain that established those constraints. This audit capability transforms AI governance from a trust-based model to a verification-based model.

**Risk management posture** frames EPG's purpose with precision. EPG reduces the likelihood of violations, enables detection when violations occur, and ensures remediation processes execute when needed. It does not eliminate risk. Risk elimination would require prohibiting useful AI deployment. Instead, EPG implements a defensible risk management posture: governed boundaries that balance operational value against regulatory requirements, combined with detection and remediation capabilities that respond when boundaries are crossed.

### 1.3 How EPG Fits the Governance Solution

EPG is one component of the Clad framework, a multi-layered framework for governing AI systems across their full operational lifecycle. EPG specifically governs S_prompt, the control surface representing instructions sent to AI models. This is one of three primary control surfaces:

**S_prompt (governed by EPG, this white paper):** The instructions, context, and guidance provided to AI models that shape their behavior. Prompt governance establishes what an AI system should do before it acts.

**S_output (governed by Runtime Output Controls, WP2):** The content produced by AI models before delivery to end users or downstream systems. Output governance ensures what an AI system produces meets quality, safety, and compliance requirements.

**S_input and S_config (governed by Monitoring & Response, SA):** User input validation and model selection/configuration governance, plus cross-component monitoring that detects when AI systems deviate from expected behavior patterns. (The meta-framework decomposes this into two formal surfaces — S_input for user input and S_config for model selection and inference parameters — but operationally they are managed by a single component.)

These components interact but remain independently valuable. An enterprise may deploy EPG to govern prompt development while output controls remain under construction. A financial services firm might implement runtime output controls immediately to prevent unauthorized trading recommendations while building out comprehensive prompt governance over subsequent quarters. This independence-with-integration property is formally proven in the meta-framework: each component provides local guarantees that compose into system-wide guarantees when multiple components are deployed together.

The integration points are deliberate. When EPG marks a prompt as compliant with constraint C_healthcare_phi, runtime output controls can reference that evaluation to inform their own risk assessment. When monitoring systems detect anomalous model behavior, they can trigger EPG re-evaluation of the active prompts. The components form a coherent governance pipeline while remaining independently deployable.

### 1.4 Who This Is For

This white paper addresses four distinct audiences within regulated enterprises:

**Enterprise governance teams** define AI policy at the organizational level. These teams—typically reporting to the CIO, CISO, or Chief Compliance Officer—establish the top-level constraints that all AI systems must satisfy. EPG provides these teams with a formal language for expressing policy requirements and a mechanism for ensuring those requirements propagate to every AI deployment.

**Department leads** translate enterprise policy into domain-specific constraints. A Chief Medical Information Officer inherits enterprise-wide data protection constraints and adds HIPAA-specific requirements for clinical AI systems. A Chief Investment Officer inherits enterprise risk management constraints and adds FINRA suitability requirements for advisory tools. EPG enables this translation process through hierarchical constraint composition while maintaining audit trail visibility back to enterprise policy.

**Project teams** build AI applications within governed boundaries. Software engineers, data scientists, and product managers craft prompts that accomplish specific business objectives while satisfying all applicable constraints. EPG provides these teams with rapid feedback on whether their prompts comply with governance requirements, removing the friction that typically makes governance feel like obstruction rather than enablement.

**Security and compliance teams** audit AI usage across the enterprise. These teams must answer regulator questions, investigate incidents, and verify that governance policies are actually enforced rather than merely documented. EPG provides verifiable audit trails that demonstrate governance enforcement with cryptographic certainty, transforming compliance from a documentation exercise into a verification process.

The remainder of this white paper provides the technical depth required to implement EPG in production environments while maintaining the business context required to justify the investment to executive leadership.

---

## 2. Principles

Enterprise Prompt Governance rests on five foundational principles. Each principle establishes a critical design decision that shapes how EPG operates in production environments. Understanding these principles is essential for both executive leadership evaluating EPG's strategic fit and technical teams implementing EPG in complex organizational contexts.

### 2.1 Governance is Constraint, Not Prescription

EPG defines what prompts must not violate. It does not define what prompts must say.

This distinction separates EPG from template-based prompt management systems. A template system maintains a library of approved prompts and requires teams to select from that library. EPG establishes constraints that prompts must satisfy and allows teams to craft prompts that meet those constraints while addressing their specific business requirements.

Consider the analogy of building codes. A municipal building code constrains how you build a structure. It specifies maximum heights, minimum fire safety measures, required load-bearing capacities, and mandatory accessibility features. It does not design your building. An architect working within those constraints retains full creative freedom to design a hospital, an office tower, or a residential complex. The building code ensures safety and compliance without prescribing architectural vision.

EPG operates identically. An enterprise constraint might prohibit disclosure of personally identifiable information (PII) in AI-generated outputs. That constraint says nothing about whether a team is building a customer service chatbot, a document summarization tool, or an incident analysis assistant. It establishes a boundary that all three use cases must respect while leaving the prompt design decisions to the teams who understand their specific business context.

Formally, constraints are predicates—functions that map a prompt to a boolean outcome:

    C : P → {true, false}

A constraint evaluates a prompt and returns true if the prompt satisfies the constraint, false otherwise. Constraints are not generators. They do not produce prompts. They validate prompts produced by other means.

This constraint-based model enables EPG to scale across diverse use cases without requiring centralized prompt authoring teams. Governance teams define boundaries. Project teams build within those boundaries. The governance layer remains constant while the application layer adapts to thousands of distinct business requirements.

### 2.2 Execution is Always Local

No organization executes an "enterprise-level prompt." Every prompt that reaches a production AI model is executed by an end user through a specific project deployment.

This principle reflects operational reality. When a customer service representative uses an AI assistant to draft a response, they are executing a prompt that was built by the customer service project team, constrained by customer service department policies, and further constrained by enterprise-wide governance requirements. But the execution event occurs at project scope. The user does not directly interact with enterprise or department constraints. They interact with a project-level system that was built to satisfy those constraints.

EPG's three-level hierarchy—enterprise, department, project—reflects this execution model. The hierarchy exists for governance composition, not execution routing. Constraints flow downward through the hierarchy during prompt development. Execution flows upward through the hierarchy during audit reporting. But the actual execution event occurs at project level.

Formally, for any prompt p that executes in production:

    ∀ p ∈ P_executed : level(p) = project

This property has significant architectural implications. EPG does not maintain enterprise-level or department-level execution environments. Those levels exist as constraint definition layers, not execution layers. When a project team deploys a prompt to production, they deploy a project-level artifact that has been validated against the complete constraint set inherited from department and enterprise levels. But the deployment itself is scoped to the project.

This design choice prevents a common governance anti-pattern: the shared service model where a central team attempts to build and operate AI systems on behalf of the entire enterprise. Shared service models fail for AI because prompt requirements are irreducibly context-dependent. A customer service prompt for healthcare claims processing cannot be genericized into a universal customer service prompt. The domain knowledge, regulatory constraints, and user expectations differ fundamentally from customer service prompts in retail, financial services, or telecommunications. EPG recognizes this reality by placing execution authority at project level while maintaining governance authority at higher levels.

### 2.3 Risk Management, Not Risk Elimination

EPG reduces the likelihood of governance violations, enables detection when violations occur, and ensures remediation processes execute when needed. It does not eliminate violations.

Risk elimination would require prohibiting AI deployment. Any system that generates novel outputs carries inherent risk of producing outputs that violate policy, even when that system operates under well-designed constraints. A healthcare chatbot built with comprehensive HIPAA-aligned prompt constraints may still generate a response that inadvertently reveals protected health information if the model encounters an adversarial input pattern. A financial analysis tool governed by strict disclosure requirements may still produce a statement that could be construed as unauthorized advice if the model misinterprets an ambiguous user query.

EPG implements a defensible risk management posture instead of pursuing unattainable risk elimination. Consider the analogy of Sarbanes-Oxley (SOX) compliance in financial systems. SOX does not prevent financial fraud. It establishes controls that make fraud detectable and traceable. When fraud occurs, SOX-compliant systems provide the audit trail required to identify what happened, who was involved, and what corrective actions are needed. The compliance posture is not "fraud never occurs." It is "when fraud occurs, we detect it, document it, and remediate it."

EPG provides equivalent capabilities for prompt governance:

**Likelihood reduction.** Constraint validation during prompt development catches violations before deployment. A prompt that includes prohibited language triggers evaluation failure, preventing that prompt from reaching production.

**Detection enablement.** Full audit trails record every prompt evaluation, providing compliance teams with the data required to detect patterns of constraint violations, identify teams that consistently struggle with specific requirements, and flag anomalous prompt behaviors that warrant investigation.

**Remediation assurance.** Version-stamped constraint sets and tamper-evident evaluation records ensure that when violations are detected, remediation teams can identify exactly which constraints were violated, which prompts caused violations, and which governance policies need strengthening.

Formally, EPG guarantees audit completeness, not universal compliance:

    ∀ p ∈ P_executed : ∃ evaluation_record(p, C_effective, timestamp, result)

Every executed prompt has a corresponding evaluation record showing which constraints were in effect, when the evaluation occurred, and whether the prompt satisfied those constraints. The system does not guarantee that result = true for all prompts. It guarantees that the evaluation occurred and the result is recorded.

This distinction is critical for executive stakeholders evaluating EPG. EPG is not an insurance policy against AI governance failures. It is a risk management framework that transforms ungoverned AI deployment into governed AI deployment, providing the visibility and control required to operate AI systems in regulated environments with defensible risk posture.

### 2.4 Audit the Artifact, Not the Process

The auditable unit in EPG is the assembled prompt evaluated against the effective constraint set. EPG does not audit whether teams followed prescribed development processes. It audits whether the prompts those teams produced satisfy governance requirements.

This principle reflects a fundamental insight about governance enforcement: process compliance is a proxy for outcome compliance, and proxies fail. A team might meticulously follow a prescribed prompt development process yet produce a prompt that violates critical constraints. Conversely, a team might bypass the prescribed process entirely yet produce a fully compliant prompt. If governance systems audit process rather than artifact, they reward the first team and punish the second, despite the first team producing a governance violation and the second team producing a compliant system.

EPG inverts this model. The evaluation question is not "did the team use the approved template?" but rather "does the assembled prompt satisfy the effective constraints?" A project team might build prompts through template composition, manual authoring, programmatic generation, or any hybrid approach. EPG remains agnostic to the construction process. It evaluates the construction output.

This artifact-centric audit model has significant practical advantages:

**It accommodates innovation.** When a team develops a novel prompt construction technique—using retrieval-augmented generation to inject domain context, employing few-shot learning to shape model behavior, or leveraging chain-of-thought prompting to improve reasoning quality—EPG evaluates the resulting prompt against constraints without requiring governance approval for the construction technique itself.

**It reduces governance overhead.** Process-based governance requires maintaining detailed procedural documentation, training teams on those procedures, and auditing procedural compliance. Artifact-based governance requires defining constraints and evaluating prompts. The latter scales far more efficiently across diverse teams with varying technical capabilities.

**It provides objective evidence.** A process audit produces subjective assessments: "the team substantially followed the procedure" or "the team deviated from step 4." An artifact audit produces objective verdicts: "the prompt satisfies constraint C" or "the prompt violates constraint C." Objective verdicts support consistent enforcement and defensible audit responses.

This principle shapes EPG's technical architecture in concrete ways. EPG's evaluation engine operates on assembled prompts, not on the source materials that contributed to those prompts. If a prompt is constructed by combining a base template, dynamic user context, and retrieved knowledge base fragments, EPG evaluates the final assembled text that will be sent to the AI model. The composition process is invisible to the evaluation layer.

### 2.5 Constraints Inherit Downward, Authority Scopes Laterally

Constraints flow downward through the hierarchy inviolably. Authority to define constraints flows laterally across domains at each level.

The downward inheritance rule is strict: a constraint established at enterprise level applies to all departments and all projects. A constraint established at department level applies to all projects within that department. Lower levels can add constraints—tightening the boundary—but cannot remove constraints established upstream. This rule ensures that enterprise-wide governance requirements cannot be bypassed by department or project teams, even inadvertently.

The lateral scoping rule is equally critical: authority to define constraints is partitioned by domain. Legal teams govern legal compliance constraints. Security teams govern data protection and access control constraints. Clinical teams govern healthcare-specific constraints. Finance teams govern financial reporting and disclosure constraints. Each domain establishes constraints within its area of authority, and those constraints apply across all levels of the hierarchy.

This dual-axis model—hierarchical inheritance, lateral domain authority—prevents both under-governance and over-governance pathologies. Without hierarchical inheritance, departments could exempt themselves from enterprise security policies by simply not adopting those policies at department level. Without lateral domain authority, a single central governance team would need expertise across legal, security, clinical, financial, and operational domains—an impossible requirement that leads to either governance bottlenecks or governance gaps.

Consider a concrete example. An enterprise security team establishes a constraint prohibiting PII in test environments. This constraint applies enterprise-wide across all departments and projects. Separately, the healthcare compliance team establishes a constraint requiring HIPAA disclaimer language in all patient-facing AI systems. This constraint applies to the healthcare department and all its projects, but does not apply to the finance or operations departments. A project team building a patient portal inherits both constraints and cannot weaken either. The project team can add project-specific constraints—for example, requiring that appointment scheduling responses include cancellation policy information—but those additions only apply within that project.

Formally, the effective constraint set at any level is the union of constraints defined at that level and all levels above:

    C_effective(project_p) = C_enterprise ∪ C_department(p) ∪ C_project(p)

This union operation ensures constraint sets can only grow, never shrink, as we move down the hierarchy. The lateral domain scoping determines which constraints appear in each C_level set, but the inheritance rule determines how those sets combine.

This principle directly addresses a concern executives frequently raise about AI governance frameworks: will governance become a centralized bottleneck that stalls innovation? EPG's answer is architectural: domain authority distributes governance decisions to the teams with relevant expertise, while hierarchical inheritance ensures those distributed decisions compose into coherent enterprise-wide policy. The result is governance that scales across hundreds of project teams without requiring every decision to flow through a central approval committee.

---

## 3. The Hierarchical Constraint Model

The hierarchical constraint model is the conceptual and technical foundation of EPG. This section provides the detailed specification required to implement EPG in production environments while maintaining sufficient business context for executive stakeholders to understand the model's architectural choices.

### 3.1 Three Governance Levels

EPG structures constraints across three levels: enterprise, department, and project. This structure mirrors the corporate organizational hierarchy that exists in regulated enterprises, providing intuitive ownership mapping that aligns governance authority with existing reporting relationships and domain expertise.

**Enterprise level** establishes organization-wide constraints that apply to every AI system regardless of business unit or use case. Enterprise constraints typically address cross-cutting concerns: data protection policies, regulatory compliance baselines, acceptable use boundaries, and brand protection requirements. The enterprise governance team—often a cross-functional body reporting to the CIO, CISO, or Chief Compliance Officer—owns constraint definition at this level.

Typical enterprise constraints include:

- Prohibition of PII in test and development environments
- Required data minimization practices for customer information
- Prohibited content categories (hate speech, self-harm, violence)
- Geographic data residency requirements
- Required disclosure of AI system involvement in customer interactions

Enterprise constraints establish the floor beneath which no department or project can operate. They represent the organization's non-negotiable governance requirements.

**Department level** translates enterprise policy into domain-specific constraints. A department in this context represents a business unit with distinct regulatory, operational, or domain requirements. Healthcare, finance, legal, human resources, and customer service functions typically map to separate departments in the EPG hierarchy, though the exact mapping should mirror the enterprise's existing organizational structure.

Department leaders—Chief Medical Information Officers, Chief Investment Officers, General Counsels, Chief Human Resources Officers—own constraint definition at this level. They inherit all enterprise constraints and add department-specific requirements that reflect their domain's regulatory environment, operational standards, and risk tolerance.

Typical department constraints include:

- Healthcare: HIPAA compliance requirements, clinical disclaimer language, scope limitations preventing diagnosis or treatment advice
- Finance: FINRA suitability standards, required disclosure language, prohibition of specific financial advice
- Legal: Attorney-client privilege protection, required confidentiality notices, prohibition of legal conclusions
- Human Resources: Equal employment opportunity compliance, anti-discrimination constraints, required privacy disclosures

Department constraints refine enterprise policy without contradicting it. They add specificity appropriate to the domain while preserving all protections established at enterprise level.

**Project level** represents individual AI deployments—the specific systems that execute prompts in production. A project might be a customer service chatbot, a document analysis tool, a code generation assistant, or an incident report automation system. Project teams include software engineers, data scientists, product managers, and business stakeholders who understand the specific use case and can define constraints that ensure the AI system serves its intended purpose without overstepping appropriate boundaries.

Project teams inherit constraints from both enterprise and department levels. They add project-specific constraints that address the unique requirements of their use case:

- A patient FAQ chatbot might add constraints requiring redirection to healthcare providers for medical questions
- A contract analysis tool might add constraints requiring flagging of non-standard clause language
- An investment research assistant might add constraints requiring citation of data sources for quantitative claims

Project constraints complete the constraint hierarchy. They represent the most specific governance layer—constraints that apply to a single AI system serving a specific business function.

This three-level structure provides clear ownership boundaries. Enterprise teams define organization-wide policy. Department leaders translate policy into domain practice. Project teams implement systems that satisfy both. Each level operates with appropriate autonomy within its scope while remaining accountable to constraints established upstream.

### 3.2 Constraint Types (Deontic Modalities)

EPG employs a deliberately minimal logical framework consisting of exactly two operators that carry logical force, plus one governance annotation that provides metadata without affecting evaluation.

**Obligation O(φ)** expresses that property φ must hold. When a constraint declares O(data_minimization_clause), the system requires that the evaluated prompt includes language implementing data minimization principles. Obligations impose positive requirements—things that must be present or true.

**Prohibition F(φ)** expresses that property φ must not hold. When a constraint declares F(pii_in_test_environments), the system requires that the evaluated prompt does not permit personally identifiable information in test or development contexts. Prohibitions impose negative requirements—things that must be absent or false.

**Governance annotation P_meta(φ)** records that property φ was considered by the governance team and no restriction was imposed. When a constraint set includes P_meta(industry_terminology), this signals "we explicitly evaluated whether to restrict industry-specific terminology and decided not to." This annotation serves governance coordination purposes but carries no logical force in constraint evaluation.

These three constructs relate in specific ways:

    F(φ) ≡ O(¬φ)

A prohibition is logically equivalent to an obligation of the negation. Prohibiting PII disclosure is equivalent to obligating non-disclosure of PII. EPG maintains both operators for expressiveness—it is often clearer to state "PII must not appear in logs" than to state "the absence of PII in logs must hold"—but treats them as interchangeable during evaluation.

Critically, P_meta is not the classical deontic permission operator P. In standard deontic logic, permission relates to obligation through the axiom:

    O(φ) → P(φ)

This axiom states that if something is obligatory, it is also permitted. But this axiom does not apply in EPG because P_meta is a governance annotation, not a logical operator. The relationship between obligation and annotation is undefined because they operate in different semantic spaces. Obligations and prohibitions participate in constraint evaluation, inheritance, and conflict detection. Annotations do not.

This design choice prevents deontic paradoxes that arise when permission is treated as a logical operator:

**Ross's paradox.** In classical deontic logic, if mailing the letter is permitted, and mailing the letter entails burning the letter or mailing it, then burning the letter or mailing it is permitted. This allows deriving permission for actions that should not be permitted through disjunction.

**Free choice permission.** The statement "you may have coffee or tea" should permit having coffee and permit having tea, but classical permission operators struggle to capture this intuition without allowing over-permissive inferences.

EPG sidesteps both paradoxes by refusing to treat permission as a logical operator. P_meta(φ) does not participate in logical inference. It cannot be combined with other constraints through boolean operators. It does not propagate through inheritance rules. It exists solely to document governance decisions for audit and coordination purposes.

This design choice has a second critical benefit: it ensures contradiction detection remains complete. EPG can definitively determine whether two constraints contradict each other by checking whether any property has both an obligation and a prohibition:

    Contradiction ≡ ∃φ : O(φ) ∈ C_effective ∧ F(φ) ∈ C_effective

If obligation and prohibition could be derived through complex logical inference involving permissions, boolean combinations, and conditional constraints, contradiction detection would become undecidable. By restricting the logical language to atomic obligations and prohibitions, EPG ensures contradictions can be detected in linear time through simple set intersection.

### 3.2a Property Language Restrictions

The properties φ that appear in obligations and prohibitions are atomic identifiers drawn from a controlled vocabulary. EPG does not allow boolean composition of properties within constraints.

This means the following are well-formed constraints:

    O(hipaa_disclaimer)
    F(pii_in_logs)
    O(data_source_citation)

But the following are not well-formed constraints:

    O(hipaa_disclaimer ∧ patient_consent)
    F(pii_in_logs ∨ phi_in_test_data)
    O(data_minimization → anonymization)

Properties are atomic. They cannot be combined with AND, OR, or IMPLIES operators. This restriction is not a limitation of EPG's technical implementation. It is a deliberate design choice that prevents three classes of problems:

**Paradox prevention.** Boolean combinations of properties enable the deontic paradoxes mentioned above. By prohibiting these combinations, EPG eliminates entire classes of logical inconsistency.

**Completeness guarantees.** Contradiction detection remains polynomial-time computable when properties are atomic. Introducing boolean operators would make contradiction detection NP-hard or undecidable depending on the operators allowed.

**Implementation clarity.** Composite constraints like O(φ ∧ ψ) raise questions about partial satisfaction. If a prompt satisfies φ but not ψ, does it partially satisfy the constraint? Should partial satisfaction block deployment? Atomic constraints eliminate ambiguity: a constraint is either satisfied or violated.

When a governance requirement genuinely involves multiple properties, EPG expresses this through multiple atomic constraints:

    O(hipaa_disclaimer)
    O(patient_consent_verification)

This representation makes explicit that both properties are independently required. The evaluation system checks each constraint separately and reports violations independently, providing clearer audit feedback than a single composite constraint would provide.

Contrary-to-duty obligations—requirements that activate when another requirement is violated—are similarly expressed as separate atomic constraints with documented relationships rather than as conditional logical expressions. If enterprise policy requires that when PII appears in an error log, it must be redacted within 24 hours, this is expressed as:

    F(pii_in_error_logs)
    O(redaction_procedure_documented)

The relationship between these constraints is recorded in governance metadata, not encoded in logical operators. This approach maintains the simplicity of the evaluation engine while providing sufficient expressiveness for real-world governance requirements.

### 3.3 Inheritance Rules (Strengthening)

Constraints propagate down the hierarchy according to strict inheritance rules that ensure lower levels cannot weaken protections established at higher levels.

**Obligations propagate down inviolably.** If O(φ) appears in the enterprise constraint set, O(φ) appears in the effective constraint set for every department and every project. Department and project teams cannot remove, modify, or weaken enterprise-level obligations.

**Prohibitions propagate down inviolably.** If F(φ) appears in the enterprise constraint set, F(φ) appears in the effective constraint set for every department and every project. Department and project teams cannot remove, modify, or weaken enterprise-level prohibitions.

**Annotations do not propagate.** If P_meta(φ) appears at enterprise level, this annotation does not appear in department or project effective constraint sets. Annotations are governance metadata scoped to the level where they are defined. They do not participate in inheritance.

**Lower levels can add obligations or prohibitions.** A department can introduce O(ψ) or F(ψ) even if neither ψ nor ¬ψ appear in the enterprise constraint set. This addition tightens the constraint boundary for that department and all projects within it. The addition is valid provided it does not create a contradiction with inherited constraints.

**Lower levels cannot introduce weakening constraints.** A department cannot add P_meta(φ) if O(φ) or F(φ) was inherited from enterprise level. The annotation has no effect on evaluation—the obligation or prohibition remains in force—but the contradictory intent signal would indicate a governance coordination failure warranting review.

The effective constraint set at any level is computed as:

    C_effective(project) = {c | c is O or F, defined at project, department, or enterprise level}

This set excludes all P_meta annotations regardless of where they appear. Annotations are available for governance audit and coordination review but do not participate in prompt evaluation.

The inheritance model implements a monotonic strengthening property: moving down the hierarchy can only add constraints, never remove them. Formally:

    C_effective(enterprise) ⊆ C_effective(department) ⊆ C_effective(project)

This property ensures that no project team can accidentally or intentionally weaken governance protections established at higher levels. The most permissive constraint set in the organization is the enterprise constraint set. Every department and project constraint set is either equal to or stricter than enterprise constraints.

This monotonic property has significant operational value. Enterprise governance teams can define organization-wide policy with confidence that their constraints will propagate to every AI system regardless of how many intervening layers exist in the organizational hierarchy. Department leaders can refine policy for their domain without risk of undermining enterprise baselines. Project teams can implement systems knowing that as long as they satisfy their effective constraint set, they are automatically in compliance with all higher-level policies.

### 3.4 Permission Semantics

EPG operates with a two-operator logical system where only obligations O(φ) and prohibitions F(φ) carry logical force. The governance annotation P_meta(φ) exists in a separate semantic space and serves governance coordination purposes rather than evaluation purposes.

This design choice creates nuanced semantics around permission that must be clearly understood:

**P_meta has no evaluation effect.** When constraint evaluation occurs, the evaluation engine considers only obligations and prohibitions. Annotations are invisible to the evaluation process. A prompt is compliant if it satisfies all obligations and violates no prohibitions, regardless of what annotations exist in the constraint set.

**P_meta has no inheritance effect.** Annotations do not propagate down the hierarchy. If the enterprise constraint set includes P_meta(clinical_terminology), department and project constraint sets do not inherit this annotation. Each level defines its own annotations independently.

**P_meta has no role in conflict detection.** The system checks for contradictions by identifying properties that have both an obligation and a prohibition. Annotations do not participate in this check. P_meta(φ) and O(φ) do not contradict each other because they operate in different semantic spaces.

However, P_meta does differ from complete absence of constraint:

**P_meta signals explicit consideration.** When an annotation P_meta(φ) appears in a constraint set, this documents that the governance team at that level explicitly evaluated whether to impose a restriction on φ and decided not to. This is governance metadata that appears in audit trails and informs governance review processes.

**P_meta triggers tension detection.** When a higher level includes P_meta(φ) and a lower level includes F(φ), the system flags this pattern as a governance tension warranting review. The lower-level prohibition is valid—inheritance rules permit tightening—but the combination suggests possible misalignment between governance levels that should be investigated.

This leads to a critical semantic property: EPG is not permissive-by-default. The absence of F(φ) does not mean φ is permitted. In classical deontic logic with a permission operator, if something is not prohibited, it is permitted. EPG rejects this equivalence. The absence of F(φ) means nothing definitive about the permissibility of φ. Only an explicit P_meta(φ) signals "we considered φ and chose not to restrict it."

This design choice prevents a common governance failure mode. In permissive-by-default systems, teams interpret the absence of prohibition as permission to proceed. When governance teams fail to enumerate every possible risk, teams deploy systems that technically satisfy the written policies but violate the governance team's intent. EPG's design prevents this failure mode by decoupling "not prohibited" from "permitted."

Consider the distinction in practice:

- **Absence of constraint.** Enterprise constraint set includes no mention of industry-specific terminology. Department teams cannot infer permission to use unrestricted terminology. They must either seek explicit permission (resulting in P_meta annotation) or proceed with understanding that future governance review might introduce restrictions.

- **Explicit annotation.** Enterprise constraint set includes P_meta(industry_terminology). Department teams can see that governance explicitly considered and chose not to restrict terminology. If a department later introduces F(industry_terminology), this will be flagged for review as a tension pattern, but the prohibition remains valid.

This semantic model supports a key governance workflow: progressive policy refinement. Early in EPG adoption, enterprise constraint sets may be sparse, containing only the highest-confidence, most critical restrictions. As governance teams gain experience with EPG, they add annotations documenting decisions about edge cases, ambiguous requirements, and domain-specific concerns. These annotations provide increasingly precise guidance to lower levels while maintaining the logical simplicity of the two-operator evaluation system.

The tension detection mechanism works as follows:

    Tension ≡ P_meta(φ) at level L_higher ∧ F(φ) at level L_lower

When this pattern is detected, EPG generates a governance review notice. The notice does not block deployment—the prohibition is valid under inheritance rules—but alerts governance coordinators to investigate whether the lower-level team misunderstood enterprise intent or whether enterprise policy should be revised to explicitly prohibit φ rather than annotating it as unrestricted.

This mechanism helps prevent governance drift, where lower levels gradually introduce restrictions that undermine the flexibility enterprise governance teams intended to preserve.

### 3.5 Worked Example: Healthcare Constraint Inheritance

A concrete example demonstrates how the hierarchical constraint model operates in a regulated healthcare enterprise.

**Enterprise Level**

The enterprise governance team, reporting to the CISO and Chief Compliance Officer, establishes organization-wide constraints:

    O(hipaa_disclaimer)
    F(pii_in_logs)
    P_meta(clinical_terminology)

The first constraint obligates that all customer-facing AI systems include disclaimer language clarifying HIPAA compliance scope. The second constraint prohibits logging personally identifiable information to system logs, test databases, or analytics platforms. The third annotation documents that the governance team explicitly considered whether to restrict clinical terminology in AI responses and decided not to impose blanket restrictions, recognizing that medical terminology is often necessary for effective healthcare communication.

**Department Level: Patient Services**

The Patient Services department, led by the Chief Medical Information Officer, inherits enterprise constraints and adds department-specific requirements:

    Inherited: O(hipaa_disclaimer), F(pii_in_logs)
    Added: F(clinical_terminology), O(empathetic_tone), O(provider_referral_required)

The department inherits both the disclaimer obligation and the PII logging prohibition. These constraints remain in full force and cannot be modified at department level.

The department adds three new constraints:

    F(clinical_terminology)

This prohibition restricts the use of medical jargon in patient-facing communications. While the enterprise governance team chose not to restrict clinical terminology organization-wide—recognizing that provider-facing tools may require precise medical language—the Patient Services department determines that patient-facing systems should avoid terminology that may confuse or alarm patients.

The combination of P_meta(clinical_terminology) at enterprise level and F(clinical_terminology) at department level triggers a governance tension flag. This is not an error. The inheritance rules permit departments to tighten constraints. But the tension flag prompts governance review to ensure the department team understands enterprise intent and that the prohibition aligns with organizational goals. The review confirms that the restriction is appropriate for patient-facing systems even though enterprise policy leaves terminology unrestricted for provider-facing tools.

    O(empathetic_tone)

This obligation requires that patient-facing AI communications employ empathetic language that acknowledges patient concerns and emotional state. Healthcare interactions frequently involve stress, uncertainty, and vulnerability. Department policy requires that AI systems reflect this context in their communication style.

    O(provider_referral_required)

This obligation requires that AI responses to clinical questions include explicit referral to healthcare providers. The system must not only refuse to provide diagnosis or treatment advice—which would be captured in a prohibition constraint—but must actively direct patients toward appropriate clinical resources.

The effective constraint set for any Patient Services project is now:

    C_effective(Patient Services) = {
        O(hipaa_disclaimer),
        F(pii_in_logs),
        F(clinical_terminology),
        O(empathetic_tone),
        O(provider_referral_required)
    }

**Project Level: FAQ Bot**

The Patient Services FAQ Bot project team builds an AI system that answers common patient questions about appointment scheduling, insurance coverage, prescription refills, and facility information. The project inherits all enterprise and department constraints and adds project-specific requirements:

    Inherited: O(hipaa_disclaimer), F(pii_in_logs), F(clinical_terminology), O(empathetic_tone), O(provider_referral_required)
    Added: O(scope_to_faq_topics), O(appointment_scheduling_context)

The project team adds two constraints that narrow the AI system's scope:

    O(scope_to_faq_topics)

This obligation requires that the FAQ bot's prompts include explicit boundaries limiting responses to the defined FAQ topic areas. Questions outside this scope should trigger a polite redirection rather than an attempt to answer with uncertain information. This constraint prevents scope creep where a narrowly-designed FAQ system begins attempting to answer complex questions it was not designed to handle.

    O(appointment_scheduling_context)

This obligation requires that appointment-related responses include context about cancellation policies, rescheduling procedures, and patient rights. This information is legally required in certain jurisdictions and operationally important for patient satisfaction.

The project team cannot weaken any inherited constraints. If the team attempted to remove F(clinical_terminology)—perhaps arguing that some technical terms are unavoidable when discussing insurance coverage—the constraint evaluation would reject the project constraint set as invalid. The prohibition was established at department level and cannot be removed at project level.

The effective constraint set for the FAQ Bot project is:

    C_effective(FAQ Bot) = {
        O(hipaa_disclaimer),
        F(pii_in_logs),
        F(clinical_terminology),
        O(empathetic_tone),
        O(provider_referral_required),
        O(scope_to_faq_topics),
        O(appointment_scheduling_context)
    }

This constraint set represents the complete governance boundary for the FAQ Bot. When the project team builds prompts for the FAQ Bot, EPG evaluates those prompts against this seven-constraint set. A prompt that satisfies all seven constraints is compliant and can be deployed to production. A prompt that violates any constraint is rejected and must be revised.

**Constraint Set Evolution**

The hierarchical model accommodates governance evolution. Consider three scenarios:

1. **Enterprise tightening.** The enterprise governance team adds F(external_data_sources) prohibiting AI systems from incorporating data from external APIs without explicit security review. This prohibition immediately propagates to all departments and projects. Existing projects must re-evaluate their prompts against the expanded constraint set. Projects that were compliant yesterday may be non-compliant today if their prompts reference external data sources.

2. **Department refinement.** The Patient Services department adds O(spanish_language_support) requiring that patient-facing systems provide bilingual support. This obligation applies to all Patient Services projects but does not affect other departments. The FAQ Bot project inherits the new obligation and must update its prompts to satisfy the requirement.

3. **Project adaptation.** The FAQ Bot project adds F(insurance_claim_status) prohibiting the FAQ bot from attempting to answer questions about specific claim status, which requires authenticated access to patient records that the FAQ bot is not designed to handle. This prohibition applies only to the FAQ Bot project and does not affect other Patient Services projects.

Each evolution point demonstrates the hierarchical model's flexibility. Governance can tighten at any level without disrupting other levels. Constraints flow downward automatically without requiring manual propagation. Project teams retain autonomy to add project-specific constraints while maintaining compliance with all upstream requirements.

This worked example demonstrates the hierarchical constraint model operating across all three levels with realistic healthcare governance requirements, showing how obligations, prohibitions, and annotations compose to create defensible AI governance boundaries that balance regulatory compliance, operational requirements, and project-specific needs.

---

## 4. Constraint Authorship and Governance

The hierarchical constraint model defines what constraints exist and how they flow. This section defines who can author constraints, what controls prevent abuse, and how changes are managed. These controls directly address the insider threat — a governance framework is only as strong as its resistance to the people who operate it.

### 4.1 Role-Based Access Control

Constraint authorship is scoped by governance level and domain. Each level has designated roles:

**Enterprise level.** The central governance team — typically the CISO office, legal department, and chief compliance officer — authors organization-wide constraints. These teams define the security, legal, and regulatory baseline that all AI systems must satisfy. Enterprise-level authorship requires enterprise governance credentials and is visible to all downstream levels.

**Department level.** Department governance leads, appointed by department heads and approved by enterprise governance, author department-specific constraints. A Chief Medical Information Officer authors clinical AI constraints; a Chief Investment Officer authors financial advisory constraints. Each governance lead has an authorized domain scope — the set of governance properties they may create constraints for. A clinical governance lead cannot author marketing constraints; a marketing governance lead cannot author clinical constraints.

**Project level.** Project leads, appointed by their department and operating within the boundaries established by enterprise and department constraints, author project-specific constraints. Project authorship is the most narrowly scoped: project leads can only add constraints that tighten governance for their specific use case.

Formally, every agent has a scope function: `scope(agent) → set of domains`. An agent can only create or modify constraints whose property belongs to their authorized domains. The system enforces this at every lifecycle event — creation, modification, deletion, and approval.

### 4.2 Dual Control with Orthogonal Approval

No constraint enters the governance system through a single actor. Every constraint lifecycle event requires two independent parties: one to propose and one to approve.

**Basic invariant.** For all non-emergency constraints: `author(c) ≠ approver(c)`. The person who writes the constraint cannot be the person who approves it. Both must have the constraint's domain within their authorized scope. This prevents a single compromised or negligent actor from unilaterally modifying governance.

**Enhanced control for Critical tier.** For constraints classified as Critical — those governing PHI, PII, financial advice, safety-critical operations, or other high-risk properties — the system requires orthogonal approval:

- The approver must belong to a different organizational reporting line than the author: `org_unit(author(c)) ≠ org_unit(approver(c))`
- For ALL Critical-tier constraints at any governance level (enterprise or department), at least one approver must be from a central, non-business-unit function: the CISO office, central risk management, or compliance. This breaks the departmental chain of command entirely. A department head cannot capture both author and approver for Critical constraints regardless of how they structure their team's reporting lines.

**Collusion limitation.** Dual control with orthogonal approval mitigates single-actor abuse and same-unit collusion. It does not prevent cross-unit collusion by determined insiders. Two individuals from different reporting lines who agree to approve each other's malicious constraints will bypass this control. Compensating controls address this residual risk: independent periodic audit of constraint changes (sampling-based, risk-tiered), anomaly detection on constraint modification patterns (frequency, scope, timing), and mandatory review of high-impact constraint changes by the cross-domain governance body (§7.4).

### 4.3 Cross-Domain Review Trigger

When a proposed constraint could interact with constraints from another governance domain, cross-domain review is triggered. The trigger conditions are:

- The constraint's property overlaps with a property governed by another domain's existing constraints (detected by controlled vocabulary intersection)
- The constraint applies to a governance level where another domain has existing constraints on related properties (detected by property ontology relationships)
- Automated conflict detection identifies potential tension (see §6.4)

Cross-domain review involves representatives from all affected domains. It is the primary mechanism for conflict prevention — catching constraint interactions at authoring time before they reach production. The process and escalation path are detailed in §6 (Conflict Resolution).

### 4.4 Change Control and Audit Trail

All constraint lifecycle events are versioned and signed:

- **Creation:** who authored, who approved, when, which domain, what property, what governance level
- **Modification:** what changed, who proposed the change, who approved, previous version preserved, rationale documented
- **Deletion or deprecation:** who proposed, who approved, replacement constraint (if any), effective date, impact assessment

Constraint version history is subject to the same audit integrity properties as interaction audit records — immutable storage (AI1), cryptographic chain integrity (AI2), independent record signing (AI3), and independent verification capability (AI4). When a regulator asks "what constraints were in effect on March 15," the enterprise produces a cryptographically verifiable record of the exact constraint set, including every modification that led to that state.

This connects to the meta-framework's Axiom 5 (temporal identity): constraints are versioned entities whose identity includes their state at a specific point in time. It also connects to enforcement requirement EA4: constraint creation, modification, and deletion require authenticated identity with authorized scope.

### 4.5 Emergency Constraint Modification ("Break Glass")

Urgent situations — an active compliance violation discovered in production, a regulatory order requiring immediate response, a security incident demanding rapid containment — require governance to act faster than the standard dual-control process allows. The break-glass protocol provides this speed within hard bounds that prevent abuse.

**Single-actor authorization.** A single authorized enterprise-level actor may impose or modify a constraint immediately. The modification is flagged as `emergency(c) = true` in the audit trail.

**Tightening only.** Break-glass modifications may only add obligations or prohibitions. They may not weaken, remove, or relax existing O or F constraints, and they may not convert existing constraints to P_meta annotations. This prevents emergency powers from being used to loosen governance protections. An emergency that requires loosening governance is not an emergency that break-glass should address — it requires the standard deliberative process.

**Mandatory expiration.** Every break-glass constraint carries a time-to-live (TTL) calibrated to industry norms:

- Healthcare and life sciences: maximum 72 hours
- Financial services (high-frequency environments): maximum 8 hours
- Energy and infrastructure: maximum 24 hours

Deployment-specific TTLs may be shorter but never longer than these defaults.

**Automatic reversion.** If post-hoc dual-control review with orthogonal approval is not completed before TTL expiry, the emergency constraint is automatically reverted to the last known-good dual-controlled state. The reversion is itself an audited event. This prevents emergency constraints from persisting indefinitely — a common vector for configuration drift in regulated environments.

**Rate limiting.** No more than a deployment-specific maximum (recommended: 3) simultaneous break-glass modifications per domain. This prevents emergency powers from being used for bulk unauthorized governance changes.

**Immediate conflict scan.** Every break-glass constraint triggers an immediate, prioritized CONTRADICTION scan against all existing constraints at affected levels. An emergency constraint that contradicts an existing constraint is flagged immediately — it still takes effect (the emergency may require it), but the contradiction is visible in the audit trail and must be resolved during post-hoc review.

The formal exception to the dual-control invariant is explicit:

    ∀ c : ¬emergency(c) → author(c) ≠ approver(c)    [standard dual control]
    ∀ c : emergency(c) → single-actor permitted, TTL enforced, tightening-only

---

## 5. Constraint Evaluability and Mandatory Decomposition

Every constraint in the governance system must be evaluable — it must be possible to determine, with a stable and repeatable answer, whether a given prompt satisfies the constraint. This requirement is non-negotiable for regulated industries where a compliance auditor may ask "was this prompt compliant?" and expect a definitive answer. This section defines the evaluability taxonomy, the mandatory decomposition protocol for constraints that resist direct evaluation, and the formal controls that prevent decomposition from becoming an accountability escape hatch.

### 5.1 Two Evaluability Classes

Every constraint in the governance system belongs to exactly one of two classes:

**Mechanically evaluable constraints (C_m)** can be checked programmatically. Their evaluation is deterministic, automated, and repeatable. The same prompt evaluated against the same mechanical constraint produces the same result every time, regardless of who runs the evaluation or when. Examples: PII pattern detection (regex or NER scan), required section presence (string or semantic similarity check), keyword and terminology checks, prompt length limits, required disclaimer presence.

**Procedurally evidenced constraints (C_p)** require qualified human attestation. Their evaluation is deterministic given the evidence — a signed attestation from a qualified reviewer is either present or absent, yielding a binary compliance determination. Examples: domain expert review ("a medical affairs officer has reviewed this prompt and attests it does not instruct diagnostic behavior"), legal sign-off, adversarial testing attestation ("red-team testing has been completed and results reviewed").

No constraint exists outside this taxonomy. If a constraint cannot be classified as either mechanically evaluable or procedurally evidenced, it cannot enter the governance system in its current form.

### 5.2 Why Semantic Constraints Are Prohibited as First-Class Elements

Consider the constraint "prompts must not produce medical advice." This is a semantic constraint — evaluating it requires human judgment about what constitutes "medical advice." Two qualified evaluators may disagree. The same evaluator may reach different conclusions on different days. This non-determinism is fatal for audit completeness.

If a regulator asks "was this prompt compliant with your medical-advice prohibition on March 15?" and the answer depends on who you ask or when you ask them, the audit trail is not a trail — it is an opinion. Regulated industries require stable, verifiable compliance determinations. Non-deterministic evaluation cannot provide them.

Therefore, semantic constraints are prohibited as first-class elements in the governance system. They must be decomposed into mechanical and procedural components before they can be operationalized.

### 5.3 Mandatory Decomposition Protocol

Decomposition transforms a semantic constraint into a set of evaluable constraints through a structured five-step process:

**Step 1: Identify semantic intent.** A domain expert articulates the full intent of the semantic constraint. For "must not produce medical advice," the intent might include: no diagnostic language, no treatment recommendations, no dosage guidance, no prognostic statements, explicit disclaimers, and explicit redirection to qualified providers.

**Step 2: Decompose into evaluable components.** The expert separates the intent into what can be automated (mechanical checks) and what requires human judgment (procedural attestations). Diagnostic language patterns can be scanned automatically; the adequacy of the overall framing requires expert review.

**Step 3: Independent review.** A second domain expert — not the original author — reviews the decomposition for adequacy. Does the set of mechanical and procedural components sufficiently capture the original semantic intent? Are there gaps?

**Step 4: Formal attestation.** The decomposition is formally attested: "this set of mechanical and procedural constraints is a sufficient representation of the original semantic intent." The attestation is signed and versioned.

**Step 5: Registration.** The decomposition is registered in the constraint system. The original semantic constraint is preserved as an intent record (C_s) — a documentation artifact that captures the governance intent but is never evaluated directly.

### 5.4 Formal Separation: Intent Records vs Operational Constraints

The formal model distinguishes three sets of constraints:

**C_s (semantic constraints / intent records):** preserved as documentation, never evaluated directly, not part of the effective constraint set C*(level). These capture what the governance team intended.

**C_m (mechanically evaluable constraints):** part of C*(level), evaluated by the mechanical satisfaction relation ⊨_m. These capture the automatable aspects of governance intent.

**C_p (procedurally evidenced constraints):** part of C*(level), evaluated by the procedural satisfaction relation ⊨_p. These capture the judgment-dependent aspects of governance intent.

The mapping `decomp: C_s → (𝒫(C_m) × 𝒫(C_p))` links each intent record to its operational decomposition. Only C_m and C_p appear in the effective constraint set, satisfaction relations, and audit records. The formal model maintains a clean separation: intent records document what governance means; operational constraints implement what governance checks.

### 5.5 Formal Soundness Relationship

The goal of decomposition is to establish a soundness relationship: if all mechanical checks pass and all procedural attestations are satisfied, then the original semantic constraint is satisfied.

Formally:

    Soundness(c_s) ⇔ ∀p, evidence :
      (∀c_m ∈ decomp_m(c_s) : p ⊨_m c_m) ∧
      (∀c_p ∈ decomp_p(c_s) : evidence ⊨_p c_p)
      → p ⊨_s c_s

This is the goal, not a guarantee. The system distinguishes two operational states:

**Soundness_claimed(c_s):** the decomposition author asserts the implication holds, attested by a domain expert. This is a professional judgment backed by attestation.

**No_counterexample_found(c_s):** red-team testing and regression testing have not falsified the soundness claim. This is empirical evidence that the decomposition works for known test cases.

The system does not equate these. It seeks to falsify soundness claims through adversarial testing. Inability to falsify is evidence, not proof. This mirrors the scientific method: a hypothesis that survives rigorous testing is credible but not proven.

Completeness is explicitly not claimed. It is possible that a prompt satisfies the original semantic intent while failing a mechanical check (the decomposition is over-tight). This is acceptable — it means some compliant prompts may be conservatively rejected, which is the safer failure mode in regulated environments.

### 5.6 Residual Gap Controls

When soundness cannot be fully demonstrated — when the decomposition captures most but not all of the semantic intent — the decomposition documents a residual gap. The residual gap is the portion of the original semantic intent not fully captured by the mechanical and procedural components.

**The residual gap is not an escape hatch.** The following controls prevent it from becoming one:

**Coverage requirement.** Each semantic intent is broken into a finite set of aspects. For Critical-tier constraints, a minimum coverage threshold applies. Core aspects must be captured in C_m or C_p and may not be deferred to the residual gap. For a constraint prohibiting PHI disclosure, direct PHI pattern detection must be in C_m. An organization cannot leave PHI detection entirely in the residual gap and claim governance.

**Maximum gap threshold.** If a decomposition's residual gap exceeds the allowable threshold, the decomposition is rejected and the constraint cannot be operationalized until a better decomposition is found. For Critical-tier constraints, the threshold is quantitative: the decomposition must demonstrate a minimum pass rate against the Gold Standard Prompt Set and regression test suite (deployment-specific, recommended minimum: 95% for PHI/safety constraints, 90% for other Critical constraints). For Standard and Low tiers, the threshold is qualitative, assessed by the cross-domain governance body. A documented gap in a Critical constraint is not merely a risk acknowledgment — without meeting the quantitative threshold and pairing with proportional compensating controls, it is a governance failure that blocks deployment.

**Compensating control requirement.** Every residual gap must be paired with a specific, actionable compensating control. "We acknowledge the gap" is not sufficient. A valid compensating control is concrete and measurable: "mechanical checks cover an estimated 80% of diagnostic language patterns; compensating control: 10% human-in-the-loop review of outputs for this project, managed by medical affairs, with monthly reporting on findings."

**Cross-domain escalation.** Any decomposition with a residual gap for a Critical-tier constraint must be reviewed and approved by the cross-domain governance body, not just the owning domain. This prevents a single domain from accepting inadequate decompositions for high-risk constraints.

**Automatic human-in-the-loop trigger.** For Critical-tier constraints with documented residual gaps, the Governance Supervisor automatically mandates a human-in-the-loop (HITL) review rate proportional to the gap severity. If mechanical checks cover an estimated 80% of the semantic intent, a minimum 20% HITL review rate applies to outputs from prompts governed by that constraint. A documented gap without a proportional HITL rate is a governance failure, not an acceptable risk acknowledgment.

### 5.7 Decomposition Verification

Initial attestation is necessary but not sufficient. Decompositions must be periodically validated to ensure they remain adequate as models evolve, regulations change, and adversarial techniques improve.

**Risk-based sampling.** Not all constraints require the same verification intensity:

- Critical constraints (PHI, financial advice, safety-critical): full red-team testing, non-author re-attestation annually, regression suite maintained and run on every decomposition change
- Standard constraints: sampling-based verification — a random subset is re-attested each cycle, regression suite maintained
- Low-risk constraints: regression suite only, re-attestation triggered by material changes

**Verification mechanisms:**

- Red-team testing: adversarial inputs designed to violate the semantic intent while passing the mechanical checks. If an adversary can craft a prompt that the mechanical checks approve but that clearly violates the original intent, the decomposition is inadequate.
- Non-author re-attestation: periodic review by domain experts other than the original decomposition authors. Fresh eyes catch assumptions that become invisible to the original team.
- Automated regression testing: known-bad test cases that mechanical checks must catch. If a previously-detected violation pattern starts passing mechanical checks (due to check updates or configuration changes), the decomposition is flagged for revision.

**Decomposition change triggers.** Any change to C_m or C_p for a decomposition triggers automated rerun of CONTRADICTION detection with all existing constraints at affected levels, heuristic rerun of tension detection for overlapping domains, and regression test suite execution.

### 5.8 Role of LLM-Assisted Evaluation

Large language models may be used as development tooling within the decomposition process: triaging which semantic constraints are hardest to decompose, flagging potential gaps in proposed decompositions, and prioritizing which constraints need human review most urgently.

LLMs are not audit mechanisms. Non-deterministic evaluation cannot serve as audit evidence. LLMs are not attestors. "An AI evaluated this and found it compliant" is not a qualified human judgment. LLM evaluation exists outside the formal governance model. It informs the humans who author decompositions and perform attestations. It does not replace them.

---

## 6. Conflict Resolution

When multiple governance domains author constraints at the same level, those constraints may interact in ways that create tension or outright contradiction. A marketing domain's obligation for conversational tone and a legal domain's prohibition on promissory language are each individually reasonable, but satisfying both simultaneously is harder than satisfying either alone. This section defines the two-phase conflict resolution model: prevent conflicts at authoring time (Phase 1), and resolve escaped conflicts at evaluation time (Phase 2).

### 6.1 Why Conflicts Occur

Conflicts arise because governance is multi-stakeholder. The legal team, the security team, the marketing team, and the clinical safety team each author constraints from their domain perspective. Each set of domain-specific constraints is internally consistent. But the combined constraint set — the union of constraints from all domains at a given level — may contain interactions that no single domain anticipated.

Tension is not the same as contradiction. Two constraints are in contradiction when no prompt can satisfy both: O(φ) and F(φ) for the same atomic property φ. Two constraints are in tension when both can be individually satisfied, but satisfying both simultaneously is harder than satisfying either alone. Contradiction is a logical property that can be detected mechanically. Tension is a practical property that requires judgment to assess.

### 6.2 Phase 1: Conflict Prevention at Authoring

When a new constraint is proposed, the system performs automated conflict detection before the constraint can be registered.

**Contradictions are hard blocks.** If the proposed constraint contradicts an existing constraint at the same level, it cannot be registered. The author must modify the proposed constraint or work with the other domain to resolve the contradiction before resubmitting.

**Tensions trigger cross-domain review.** If the system detects potential tension between the proposed constraint and existing constraints from other domains, cross-domain review is triggered (§4.3). Representatives from all affected domains must review the interaction and choose a resolution before the constraint is registered.

**Resolution options at authoring:**

- **Modify constraints.** One or both constraints are revised to eliminate the tension. This is the preferred resolution.
- **Add a precedence rule.** A precedence annotation is added to the Enterprise Precedence Table (§6.5): "when these two constraints are both applicable and create tension, constraint A takes precedence." The rationale is documented.
- **Accept the tension** — restricted by risk tier:
  - **Critical tier: not permitted.** Tension in Critical-tier constraints must resolve to either modification or a precedence rule. Unresolved tension in Critical-tier constraints is a governance failure.
  - **Standard tier:** permitted with cross-domain governance body approval and documented compensating controls.
  - **Low tier:** permitted with owning domain approval.

### 6.3 Phase 2: Automated and Arbiter Resolution at Evaluation

If a conflict surfaces during prompt evaluation that was not caught at authoring, runtime resolution proceeds in three stages designed to keep production systems running while ensuring governance accountability:

**Stage 1: Enterprise Precedence Table.** The system consults the Enterprise Precedence Table (§6.5). If the conflicting constraints have a registered precedence rule, the rule is applied automatically. No human intervention is needed. This handles known conflict patterns in high-volume production flows without committee lag.

**Stage 2: Default global priority.** If no specific precedence rule exists for this constraint pair, the system applies the default domain priority ordering established at the enterprise level — for example, Safety > Legal > Security > Compliance > Business. The higher-priority domain's constraint takes precedence. The interaction proceeds without blocking. The conflict event is simultaneously flagged for post-hoc arbiter review to create a permanent specific rule.

**Stage 3: Arbiter escalation (non-blocking).** The cross-domain governance arbiter reviews the conflict post-hoc to create a permanent resolution — either a new precedence rule in the Enterprise Precedence Table or a constraint modification through the standard authoring process (§4). Arbiter action is required but does not block the interaction. The default global priority already resolved it at runtime.

**Anti-circularity requirement.** The arbiter's resolution must introduce a structural fix — a precedence rule or constraint modification — that prevents the same conflict from recurring. Arbiter resolutions that do not produce a permanent fix are rejected. This prevents an escalation loop where the same conflict is repeatedly flagged without resolution.

### 6.4 Formal Conflict Detection

**CONTRADICTION** is a formal predicate, mechanically detectable, and complete for the atomic property fragment:

    CONTRADICTION(c₁, c₂) ≡ c₁ = O(φ) ∧ c₂ = F(φ)  for some atomic φ ∈ Φ

Since properties are atomic and independent (§3.2a), pairwise contradiction detection is complete. Any unsatisfiable pair of O/F constraints over independent atomic properties is detected by this predicate. Multi-constraint unsatisfiability where each pair is individually satisfiable but the set as a whole is not does not arise for the supported fragment — atomic O/F constraints over independent properties are jointly satisfiable if and only if they are pairwise satisfiable.

**TENSION** is an advisory classification, not a formal predicate. It is not sound, not complete, and no theorem or formal guarantee depends on it. It is a governance process aid. Detection heuristics include: the same atomic property referenced by constraints from different domains, properties related via the enterprise property ontology, and LLM-assisted analysis flagging potential semantic interactions (advisory, non-authoritative). Tensions are soft flags that trigger cross-domain review. They do not block constraint registration.

All formal guarantees in this framework are based on CONTRADICTION detection only. TENSION detection is operationally valuable but carries no formal weight.

### 6.5 Enterprise Precedence Table

The Enterprise Precedence Table is a machine-readable priority matrix maintained at the enterprise level. It serves two functions:

**Default domain ordering.** A global priority ranking across governance domains — for example, Safety > Legal > Security > Compliance > Business. This ordering is applied automatically during Phase 2, Stage 2 when no specific precedence rule exists. It prevents production systems from blocking on unknown conflicts while maintaining a defensible governance posture (safety and legal always take precedence over business preferences).

**Specific constraint-pair rules.** Known tension patterns are registered with explicit precedence decisions and documented rationale. When two specific constraints conflict, the table specifies which takes precedence and why. These rules are created through the standard authoring process or by arbiter resolution during Phase 2.

The precedence table is itself a governed, versioned, auditable artifact. Changes follow the standard authorship process (§4) with enterprise-level dual control. The table is the mechanism that transforms conflict resolution from a committee process into an automated, auditable system — essential for high-volume production AI systems where committee-speed resolution would constitute a denial of service.

---

## 7. Lateral Authority and Domain Isolation

Hierarchical inheritance governs how constraints flow vertically — from enterprise to department to project. Domain isolation governs how constraints are bounded horizontally — preventing one governance domain from interfering with another. Without lateral isolation, a marketing governance lead could inadvertently (or intentionally) weaken the legal team's constraints. This section defines the domain model, scope partitioning, isolation guarantees, and the mechanisms for detecting indirect cross-domain effects.

### 7.1 Domain Model

A domain is a bounded area of governance authority. Typical domains include legal, security, data privacy, marketing, clinical safety, and financial compliance. Domains are defined at the enterprise level by central governance — individual departments cannot create or modify domain definitions.

Each domain has three defining attributes:

- A designated set of authorized agents (authors and approvers) who may operate within the domain
- A defined scope of properties from the enterprise controlled vocabulary (§3.2a) that the domain may govern
- A set of governance levels at which the domain may author constraints

Property vocabulary governance is critical for domain isolation. Adding a new property to the controlled vocabulary requires enterprise governance approval. If the new property falls in a region where multiple domains have scope, cross-domain review of the property definition is triggered before it can be used in constraints. This prevents a subtle form of gaming: creating new fine-grained properties to avoid overlap detection with existing domains.

### 7.2 Scope Partitioning

**Direct overlap** is detected formally through set intersection over canonical property identifiers. If `scope(D₁) ∩ scope(D₂) ≠ ∅`, the domains have overlapping authority over some properties. For the overlap region:

- The overlap is explicitly defined and documented by enterprise governance
- Constraints governing properties in the overlap region require approval from all overlapping domains (joint authorship)
- No single domain can unilaterally create, modify, or delete constraints in the overlap region

For non-overlapping domains — where `scope(D₁) ∩ scope(D₂) = ∅` — complete isolation is enforced by scope checks at every constraint lifecycle event.

**Indirect interaction** is an acknowledged limitation. Actions in one domain's scope may materially affect risk in another domain's scope without sharing any property identifiers. A marketing constraint `O(use_layman_terms_exclusively)` could suppress a legal constraint `O(standard_legal_disclaimer)` if layman reformulation removes required legal language. These interactions are not detected by set-intersection scope analysis because the properties are formally separate.

Indirect interactions are partially detected through tension heuristics (§6.4) and impact analysis (§7.3). The formal Scope Isolation theorem (Appendix A.13) covers direct overlap only and explicitly states this limitation.

### 7.3 Domain Isolation Guarantee and Global Impact Analysis

**Direct isolation** is enforced by the constraint authorship system. An agent in domain D₁ cannot create, modify, or delete constraints in domain D₂'s exclusive scope. Shared-scope constraints require approval from all overlapping domains. These scope checks execute at every lifecycle event — creation, modification, deletion, and approval.

**Indirect effect detection** operates through Global Impact Analysis. When a new constraint is proposed, it is tested against a Gold Standard Prompt Set maintained by each domain. The Gold Standard set contains representative prompts that are known to satisfy each domain's critical constraints. Impact is measured: does the new constraint cause any Gold Standard prompt to fail a constraint from another domain?

If yes, the new constraint is flagged for cross-domain review and treated as if it were in shared scope — regardless of whether the properties formally overlap. If no, the constraint proceeds through normal authoring.

Impact analysis is empirical, not exhaustive. It detects known interaction patterns but cannot prove absence of indirect effects. This is explicitly acknowledged in the formal model.

**Adversarial diversity requirement.** Gold Standard Prompt Sets must be constructed or audited by a party independent of the constraint authors in that domain. This prevents "thinning" the set to allow specific constraints to pass impact analysis undetected. The independent party may be the cross-domain governance body, an internal red team, or an external assessor — but it must not be the team proposing the new constraint.

### 7.4 Cross-Domain Governance Body

For enterprises with multiple overlapping domains, a standing cross-domain governance body is required for Critical-tier deployments. Its composition includes one representative from each domain, chaired by enterprise governance, plus an independent compliance representative not affiliated with any single domain.

Responsibilities:

- Resolve constraint tensions flagged by cross-domain review
- Serve as the arbiter for Phase 2 conflict resolution (§6.3)
- Periodically review the domain model and scope partitioning for adequacy
- Approve changes to domain definitions and scope boundaries
- Review and approve residual gaps for Critical-tier decompositions (§5.6)

**Governance risk.** The cross-domain governance body can centrally override domain isolation by redefining domain scopes. This concentration of authority is itself a governance risk. Compensating controls include: scope redefinition requires notification to all affected domains plus a minimum 5-business-day review period; scope changes are subject to audit integrity properties (AI1-AI4); the body's composition must include diversity across reporting lines with no single business unit holding a majority.

The Scope Isolation theorem in Appendix A.13 holds relative to fixed domain definitions and explicitly does not limit the governance body's scope-redefinition authority.

---

## 8. Connection to the Meta-Framework

EPG does not exist in isolation. It is one component of the Clad framework, formally defined in the meta-framework (v5). This section maps EPG's concepts to the meta-framework's component model, establishing how EPG participates in the broader governance architecture.

### 8.1 EPG as Component g_EPG

EPG instantiates the governance component g_EPG defined in the meta-framework:

- **Surfaces:** S = {S_prompt} — EPG governs the prompt surface only
- **Constraints:** C = the hierarchical constraint set C*(project) defined in this white paper
- **Evaluation:** E = mechanical evaluation (⊨_m) combined with procedural attestation (⊨_p)
- **Audit:** A = prompt audit records (evaluation records per meta-framework §9)
- **Hard requirements:** R_hard = ∅ — EPG is independently deployable. It provides its full guarantees without any other governance component present.
- **Soft requirements:** R_soft = {organizational governance context} — EPG's evaluation effectiveness improves when ROC provides output context and MDR provides monitoring data, but its guarantees do not depend on them.

### 8.2 Enforcement Requirements

EPG requires the meta-framework's enforcement architecture to achieve full governability of the prompt surface:

- **EA1 (Chokepoint enforcement):** All AI interactions must pass through EPG enforcement points. No path from user input to model invocation may bypass EPG.
- **EA2 (Identity binding):** Every model invocation is bound to a governed project identity. Anonymous or unattributed model calls are blocked.

Without EA1 and EA2, the governability classification of S_prompt is partial rather than full — EPG's guarantees apply only to interactions that voluntarily pass through it. Shadow AI and direct API access bypass governance entirely.

### 8.3 Audit Chain Participation

EPG produces evaluation records (component-signed) for each governed interaction. These records include the interaction identifier, constraint evaluations with version stamps for every applicable constraint, a cryptographic hash of the assembled prompt, and a version manifest of all governed elements.

Records are signed with EPG's KMS key and hash-chained per the meta-framework's audit integrity properties (AI2-AI3). During EPG failure, the Governance Supervisor produces process records (supervisor-signed) that maintain chain continuity without evaluation content — a weaker form of evidence that documents the failure rather than the evaluation.

### 8.4 Interface Contracts

EPG provides data to companion components through the meta-framework's interface contract mechanism:

- **EPG to ROC:** the assembled prompt, the effective constraint set C*(project), the prompt audit record, and the interaction identifier. These are soft requirements for ROC — they enhance ROC's evaluation effectiveness but are not required for ROC's guarantees.
- **EPG to MDR:** the prompt audit record and constraint version history. These enable monitoring systems to detect governance drift and perform root-cause analysis on incidents.

### 8.5 Failure Posture

EPG declares a failure posture per risk tier, consistent with the meta-framework's failure semantics:

- **Critical workloads:** fail-closed. No prompt proceeds to the model without governance evaluation. If EPG is unavailable, the interaction is blocked.
- **Standard workloads:** fail-open-flagged. If EPG is unavailable, the interaction proceeds but is flagged as ungoverned in the audit trail.
- **Low-risk workloads:** configurable per deployment.

The binding between data classification and failure posture is itself a governed constraint — subject to the same authorship, dual control, and audit requirements as any other constraint.

---

## 9. Operational Guidance

This section translates EPG's formal model into practical adoption guidance for enterprises. It covers phased rollout, risk-tiered application, change management, constraint libraries, tooling, and the framework's explicit limitations.

### 9.1 Phased Adoption

EPG adoption follows a maturity progression. Each phase provides independently valuable governance; later phases build on earlier ones.

**Phase 1 (3-6 months): Enterprise baseline.** Establish enterprise-level constraints covering security, legal, and compliance baselines. Deploy the controlled property vocabulary. Create the Enterprise Precedence Table with default domain ordering. Stand up the cross-domain governance body. Deploy constraint authoring and evaluation tooling.

**Phase 2 (6-12 months): Department governance.** Add department-level constraints for the highest-risk business units first. Adopt the first constraint libraries (§9.4). Establish Gold Standard Prompt Sets and impact analysis for each domain. Begin decomposition verification cadence for Critical-tier constraints.

**Phase 3 (12-18 months): Full coverage.** Extend governance to all departments and project-level constraints. Establish full verification cadence across all risk tiers. Risk-tiered governance is fully operational. Constraint change management pipelines are mature.

**Phase 4 (18+ months): Optimization.** Develop organization-specific constraint libraries. Contribute to cross-organization library sharing. Conduct maturity assessments. Optimize governance overhead based on operational data.

### 9.2 Risk-Tiered Application

Not all AI interactions require the same governance intensity. EPG maps to three risk tiers:

**Critical** (PHI, PCI, financial advice, safety-critical operations): full constraint evaluation, fail-closed posture, orthogonal dual control with central approver, full verification cadence (annual red-team, continuous regression), accept-tension prohibited, complete audit chain with HITL trigger for residual gaps.

**Standard** (internal tools, non-sensitive business processes): full constraint evaluation, fail-open-flagged posture, standard dual control, sampling-based verification, complete audit chain.

**Low** (development, sandbox, experimentation): enterprise constraints only, fail-open-flagged or configurable posture, standard dual control, regression-only verification, interaction logging.

### 9.3 Constraint Change Management

Constraints are governance artifacts with production impact. They require the same change management discipline as production code.

**Staging environment.** New or modified constraints are tested against the Gold Standard Prompt Set (§7.3) and the regression suite before deployment to production. A constraint that breaks existing Gold Standard prompts is caught before it affects live interactions.

**Canary deployment.** For high-volume production systems, new constraints can be deployed to a subset of interactions first, with monitoring for unexpected failures or conflicts. If the canary shows elevated failure rates or unexpected tension patterns, the deployment is halted.

**Rollback.** Every constraint change has an automated rollback path to the previous known-good version. If a newly deployed constraint causes production issues, the system can revert to the prior state within minutes, not hours.

**Change windows.** For Critical-tier workloads, constraint changes follow a defined change window with notification to all affected project teams. Surprise constraint changes in production are a governance failure, not a governance feature.

### 9.4 Constraint Libraries

Pre-decomposed, industry-vetted constraint packages reduce the per-organization authoring burden. A "HIPAA Prompt Constraints v1.0" package, for example, provides a set of enterprise-level constraints derived from the HIPAA Privacy Rule and Security Rule, with pre-attested decompositions, known residual gaps, and regression test suites.

Libraries are versioned using semantic versioning (major.minor.patch), maintained by domain experts, and subject to the same governance lifecycle as custom constraints.

**Local verification gate.** Organizations cannot simply import a library and activate it. They must:

- Review each constraint and decomposition against their specific technical context
- Attest that the decompositions are adequate for their deployment (vendor decomposition may not match local model behavior)
- Run the library's regression test suite against their actual models and prompts. This is mechanically enforced: the system verifies that the test suite has been executed and passed on local infrastructure before transitioning library constraints from "Registered" to "Active." Paper attestation without test execution is not permitted.
- Minimum coverage: local test execution must cover at least the library's defined regression suite in full. Organizations may add local test cases but may not skip library-defined cases.

**Liability.** The adopting organization bears responsibility for constraint adequacy, not the library maintainer. The library provides a starting point; local attestation provides accountability.

### 9.5 Tooling Recommendations

Effective EPG deployment requires tooling support across several dimensions:

- Constraint authoring IDE with conflict detection and controlled vocabulary enforcement
- Automated mechanical evaluation engine with regression test management
- Procedural attestation workflow system with evidence capture and signing
- LLM-assisted triage for decomposition adequacy and tension detection
- Gold Standard Prompt Set management and impact analysis tooling
- Dashboard for constraint coverage, evaluation results, verification status, and audit compliance
- Enterprise Precedence Table editor with change control integration

### 9.6 Limitations

EPG provides governance of the prompt surface. This section states explicitly what EPG does and does not guarantee.

**What EPG guarantees (formal commitments):**

- Audit completeness: every governed prompt is evaluated against every applicable constraint, with a tamper-evident, version-stamped record
- Contradiction freedom: no pair of constraints in the effective set is contradictory (for the atomic property fragment)
- Scope isolation: no agent can create or modify constraints outside their authorized domain scope (under fixed domain definitions)
- Decomposition coverage: every semantic intent has a registered decomposition (existence, not quality)

**What EPG does not guarantee:**

- Output correctness: EPG governs prompts, not model outputs. A governed prompt can still produce non-compliant output due to model behavior, user input, or inference configuration. Output governance requires WP2 (Runtime Output Controls).
- Content accuracy: EPG ensures prompts include required elements and exclude prohibited elements. It does not ensure the model's response is factually correct.
- Model behavior: EPG cannot prevent hallucination, jailbreaking, or emergent model behavior. These require model-level and output-level controls.

**What EPG provides as governance-process tools (operationally valuable, not formally proven):**

- Tension detection: advisory heuristics that flag potential constraint interactions. Valuable for governance practice but not mathematically guaranteed to be sound or complete.
- Impact analysis: empirical testing of indirect cross-domain effects via Gold Standard Prompt Sets. Detects known patterns but cannot prove absence of indirect effects.
- Decomposition verification: red-team testing and re-attestation that seek to falsify soundness claims. Provides empirical confidence, not logical proof.
- Collusion detection: periodic audit sampling and anomaly detection on constraint changes. Catches patterns of abuse but cannot prevent determined adversaries.

The formal model uses a restricted deontic fragment — atomic properties with two operators (O and F) — that does not capture all real-world governance complexity. This is a deliberate design choice for tractability, not an oversight. The restricted fragment enables complete contradiction detection, a property that would be undecidable in a richer logic.

EPG is one component of a complete governance solution. It must be complemented by runtime output controls (WP2), monitoring and response (SA), and organizational controls (training, role-based access management, change management processes) to achieve comprehensive AI governance.

---

## Appendix A: Formal Model

This appendix contains the complete, authoritative EPG formal model. It extends and supersedes the standalone formal model document (`formal-model.md`). The formal model uses symbolic logic throughout; refer to §3 for business-language explanations of these concepts.

### Conservative Extension Argument

The formal constructs introduced beyond the base model (RBAC, domain isolation, conflict detection, decomposition mapping) extend the existing formal model without altering the truth of existing theorems. New symbols (`author`, `approver`, `scope`, `decomp`, `CONTRADICTION`, `shared_scope`, `precedence`) operate on the governance meta-layer — who can create constraints and how they are checked. They do not change the satisfaction relation for a given C*(level) and prompt p. They do not change the inheritance rules. They do not change the evaluability classification or evaluation functions. Existing theorems (Audit Completeness, No Evaluability Gap) hold over the unchanged logical core. New theorems operate over the governance meta-layer and are independent of the logical core.

The logical core uses a two-operator deontic fragment {O, F} over atomic properties (§3.2a). P_meta is a governance annotation outside this fragment. The standard axiom O(φ) → P(φ) does not apply because P_meta is not a deontic operator.

### A.1 Primitive Sets and Domains

```
L = {enterprise, department, project}     — governance levels
C = C_m ⊎ C_p                            — operational constraints (disjoint union)
C_s                                       — semantic constraints (intent records,
                                            NOT part of C, documentation only)
P = set of all prompts                    — the governed artifacts
A = set of all agents                     — authors/owners of constraints
D = set of all domains                    — legal, security, data_privacy, etc.
T = totally ordered set of time points    — for versioning and audit
V = set of audit records                  — evaluation evidence
Φ = controlled vocabulary of atomic       — enterprise-governed property identifiers
    property identifiers
```

### A.2 Ordering on Governance Levels

```
enterprise ≻ department ≻ project
(≻ denotes "governs over")
(L, ≻) is a strict total order.
```

### A.3 Core Functions

Base functions (unchanged):
```
level   : C → L           — assigns constraint to governance level
owner   : C → A           — who authored the constraint
domain  : C → D           — which domain the constraint belongs to
ver     : C × T → C_t     — version of constraint c active at time t
```

RBAC and governance meta-layer (new):
```
scope    : A → 𝒫(D)       — domains agent may author constraints for
approve  : C → A           — who approved the constraint
emergency: C → {true, false}  — break-glass flag

Dual control invariant:
  ∀ c ∈ C : ¬emergency(c) → author(c) ≠ approver(c)

Orthogonal approval (Critical tier):
  ∀ c ∈ C : risk_tier(c) = critical ∧ ¬emergency(c)
    → org_unit(author(c)) ≠ org_unit(approver(c))
    ∧ ∃ a ∈ approvers(c) : org_unit(a) ∈ {CISO, central_risk, compliance}

Break-glass bounds:
  ∀ c ∈ C : emergency(c)
    → tightening_only(c) ∧ ttl(c) ≤ max_ttl(industry(c))

Cross-domain review:
  cross_domain_reviewed : C → {true, false}

Enterprise Precedence Table:
  precedence : C × C → {c₁_wins, c₂_wins, unresolved}
```

### A.4 Constraint Taxonomy

Evaluability classes (unchanged in structure):
```
C_m = mechanically evaluable constraints
C_p = procedurally evidenced constraints
C = C_m ⊎ C_p              (operational constraints, disjoint)
```

Semantic constraints and decomposition mapping (new):
```
C_s = semantic constraints (intent records, NOT part of C)

decomp : C_s → (𝒫(C_m) × 𝒫(C_p))
  Maps each intent record to its operational decomposition.

⊨_s : conceptual satisfaction relation for semantic constraints
  Used in soundness claims only, NOT in evaluation machinery.
```

### A.5 Hierarchical Inheritance

```
C*(enterprise) = C(enterprise)
C*(department) = C(enterprise) ∪ C(department)
C*(project)    = C(enterprise) ∪ C(department) ∪ C(project)

Generally: C*(l) = ⋃{C(l') | l' ≽ l}

Monotonicity (inviolability):
  ∀ l₁, l₂ ∈ L : l₁ ≻ l₂ → C*(l₁) ⊆ C*(l₂)

Strengthening rules:
  ∀ l₁ ≻ l₂ :
    O(φ) ∈ C*(l₁)  →  O(φ) ∈ C*(l₂)     — obligations propagate
    F(φ) ∈ C*(l₁)  →  F(φ) ∈ C*(l₂)     — prohibitions propagate
    ¬∃ c ∈ C(l₂) that negates any c' ∈ C*(l₁)

Only O and F constraints participate in inheritance.
P_meta annotations are NOT part of C*(level).
```

### A.6 Permission Semantics

```
P_meta(φ) is a governance annotation, NOT a deontic operator.

Properties:
  - NO inheritance effect
  - NO evaluation effect
  - NO role in CONTRADICTION detection
  - For satisfaction and inheritance: equivalent to absence
  - For meta-governance (audit, review triggers): NOT equivalent
    to absence — signals "considered"

The system is NOT permissive-by-default:
  ¬F(φ) does NOT entail P_meta(φ) or any form of permission.
```

### A.7 Lateral Authority Scoping

```
Authorship constraint:
  ∀ c ∈ C : domain(c) ∈ scope(owner(c))

Direct overlap:
  shared_scope(D₁, D₂) = scope(D₁) ∩ scope(D₂)

Joint authorship for shared scope:
  ∀ c : property(c) ∈ shared_scope(D₁, D₂)
    → approved_by(c, D₁) ∧ approved_by(c, D₂)

Indirect interaction (advisory, not formal):
  affects(D₁, D₂) — captures known systemic couplings
  Detected via impact analysis (§7.3), NOT by scope intersection
```

### A.8 Conflict Detection and Resolution

```
CONTRADICTION predicate (formal, complete for atomic fragment):
  CONTRADICTION(c₁, c₂) ≡ c₁ = O(φ) ∧ c₂ = F(φ)
    for some atomic φ ∈ Φ

  Completeness: pairwise check is sufficient for independent
  atomic properties. Joint satisfiability of the full set follows
  from pairwise satisfiability in this fragment.

TENSION classification (advisory, NOT formal):
  Heuristic, not sound or complete.
  No theorem depends on TENSION.

Enterprise Precedence Table:
  precedence : C × C → {c₁_wins, c₂_wins, unresolved}
  default_priority : D → ℕ  (domain priority ordering)

Resolution protocol:
  Phase 1: authoring-time prevention (CONTRADICTION blocks,
           TENSION triggers review)
  Phase 2: automated precedence → default priority → arbiter
  Anti-circularity: arbiter resolutions must produce permanent
           structural fixes
```

### A.9 Decomposition Soundness

```
Soundness relationship (goal, not guarantee):
  Soundness(c_s) ⇔ ∀p, evidence :
    (∀c_m ∈ decomp_m(c_s) : p ⊨_m c_m) ∧
    (∀c_p ∈ decomp_p(c_s) : evidence ⊨_p c_p)
    → p ⊨_s c_s

Operational approximation:
  No_counterexample_found(c_s) — from tests and reviews.
  NOT equated with logical soundness.

Completeness NOT claimed:
  Over-tight decomposition may reject compliant prompts
  (conservative failure mode).

Residual gap controls:
  Coverage requirements, maximum gap thresholds,
  compensating control mandates, HITL trigger (§5.6).

Decomposition change triggers:
  Any change to decomp(c_s) reruns CONTRADICTION detection
  on affected levels.
```

### A.10 Satisfaction Relations

```
⊨_m : P × C_m → {⊤, ⊥}           — automated, deterministic
⊨_p : Evidence × C_p → {⊤, ⊥}    — human-attested, deterministic
                                      given evidence

Deontic satisfaction:
  p ⊨ O(φ)  iff  φ holds in p
  p ⊨ F(φ)  iff  φ does not hold in p
```

### A.11 Core Principles (Formal)

```
Principle 1 (Constraint, not prescription):
  ∀ c ∈ C : c ∈ (P → {⊤, ⊥})

Principle 2 (Execution is local):
  ∀ p ∈ P_executed : level(p) = project

Principle 3 (Risk management):
  System guarantees: ∀ p ∈ P_executed : audit(p,t) is COMPLETE ∧ IMMUTABLE
  System does NOT guarantee: ∀ p ∈ P_executed : ∀ c ∈ C*(project) : p ⊨ c

Principle 4 (Audit the artifact):
  ∀ p ∈ P_executed, ∀ t ∈ T :
    ∃ audit(p,t) = {(c, ver(c,t), eval(c,p)) | c ∈ C*(project)}

Principle 5 (Downward inheritance, lateral scoping):
  Inheritance: C*(l) = ⋃{C(l') | l' ≽ l}
  Scoping: ∀ c ∈ C : domain(c) ∈ scope(owner(c))
```

### A.12 Audit Record

```
audit(p, t) =
  { (c, ver(c,t), ⊨_m(p,c))                       | c ∈ C*_m(project) }
∪ { (c, ver(c,t), ⊨_p(evidence(p,c),c), attestor) | c ∈ C*_p(project) }

Extended fields:
  - conflict_detection_results : CONTRADICTION scan outcome
  - precedence_rule_applied : reference to precedence table entry (if any)
  - decomposition_version : ver(decomp(c_s), t) for each c derived from C_s
  - emergency_flag : emergency(c) for any break-glass constraints
```

### A.13 Theorems

Existing theorems (unchanged):

```
THEOREM (Audit Completeness):
  ∀ p ∈ P_executed : audit(p,t) is total over C*(project)
  ∧ audit(p,t) is deterministic
  ∧ audit(p,t) is immutable

THEOREM (No Evaluability Gap):
  ∀ c ∈ C*(project) : c ∈ C_m ∨ c ∈ C_p
```

New theorems (with explicit preconditions and scope):

```
THEOREM (Contradiction Freedom — Pairwise, Atomic Fragment):
  After Phase 1 authoring review, no pair of constraints in
  C*(level) is contradictory per the CONTRADICTION predicate
  over atomic properties φ ∈ Φ.

  Scope: complete for the atomic fragment. Multi-constraint
  and semantic interactions are handled by governance process
  (tension detection, cross-domain review), not formal guarantee.

THEOREM (Scope Isolation — Direct, Under Fixed Definitions):
  No agent can create, modify, or delete constraints outside
  their authorized domain scope, given fixed domain definitions.

  Scope: does NOT cover indirect effects (addressed by impact
  analysis §7.3), scope redefinition by the cross-domain
  governance body, or constraints authored via break-glass.

THEOREM (Decomposition Coverage — Existence, Not Quality):
  Every semantic intent in C_s has a registered decomposition
  in decomp(c_s).

  Scope: guarantees existence, not soundness or adequacy.
  Soundness is approximated empirically (§5.7). Coverage
  quality for Critical-tier constraints is governed by
  residual gap controls (§5.6).
```

### A.14 Limitations of the Formal Model

The formal model guarantees properties of the governance process: who can author what, contradictions are detected, audit is complete, scope is isolated. It does not guarantee properties of the governed artifacts beyond what the constraints specify — content correctness, semantic completeness, or model behavior are outside scope.

Mechanisms that are governance-process tools — tension detection, impact analysis, residual gap review, collusion detection — are operationally valuable but not mathematically proven. They are best-effort controls, not formal guarantees.

This distinction is critical for the target audience: formal guarantees are hard commitments that can be verified and audited. Governance-process tools are organizational controls that improve outcomes but cannot be reduced to logical proofs.

---

## Appendix B: Worked Examples

### B.1 Healthcare: Patient-Facing Chatbot

**Enterprise constraints:**
- `O(hipaa_disclaimer)` — every prompt must include HIPAA-compliant disclaimer language
- `F(pii_in_logs)` — prompts must never include instructions that would cause PII to appear in system logs
- `F(medical_diagnosis)` — prompts must not instruct the model to diagnose conditions
- `P_meta(clinical_terminology)` — enterprise has considered clinical terminology and imposes no restriction

**Department constraints (Patient Services):**
- Inherits `O(hipaa_disclaimer)` and `F(pii_in_logs)` — cannot change
- Adds `F(clinical_terminology)` — patients are not clinicians; layman terms required. This is a valid tightening of P_meta (which imposes no restriction). Flagged for tension review because P_meta existed at enterprise level; review confirms department rationale is sound.
- Adds `O(empathetic_tone)` — patient-facing communications require empathetic framing
- Adds `O(redirect_to_provider)` — prompts must instruct the model to redirect medical questions to qualified providers

**Project constraints (Patient FAQ Chatbot):**
- Inherits all enterprise and department constraints (5 total)
- Adds `O(scope_to_faq_topics)` — prompt must limit the model to answering questions within the FAQ knowledge base
- Adds `O(cite_sources)` — prompt must instruct the model to cite specific FAQ entries

**Effective constraint set at project level:**
C*(project) = {O(hipaa_disclaimer), F(pii_in_logs), F(medical_diagnosis), F(clinical_terminology), O(empathetic_tone), O(redirect_to_provider), O(scope_to_faq_topics), O(cite_sources)}

**Decomposition example: F(medical_diagnosis)**

Semantic intent: "The AI system must not produce output that could be construed as diagnosing a medical condition."

Mechanical components (C_m):
- Pattern scan for diagnostic language: regex and NER-based detection of diagnostic phrases ("you have," "this indicates," "the symptoms suggest," "diagnosis:")
- Required disclaimer presence: prompt must include explicit instruction "Do not diagnose conditions or suggest specific diagnoses"
- Scope limitation: prompt must include instruction to refuse requests for diagnosis

Procedural components (C_p):
- Medical affairs review: a qualified medical affairs officer reviews the prompt and attests that its framing adequately prevents diagnostic behavior
- Adversarial testing: red-team testing with prompts designed to elicit diagnostic language; results reviewed and attested by medical affairs

Residual gap: mechanical checks cover an estimated 85% of diagnostic language patterns based on regression testing against a corpus of known diagnostic phrases. The remaining 15% includes novel or indirect diagnostic formulations. Compensating control: 15% HITL review rate on chatbot outputs, managed by medical affairs, with monthly reporting.

**Conflict example: O(empathetic_tone) vs F(promissory_language)**

The department's obligation for empathetic tone (authored by Patient Experience domain) creates tension with Legal's prohibition on promissory language. Empathetic language like "we will make sure you get the care you need" could be construed as a promissory commitment.

Phase 1 resolution: cross-domain review between Patient Experience and Legal. Resolution: add a precedence rule to the Enterprise Precedence Table — F(promissory_language) takes precedence over O(empathetic_tone). The project team must find empathetic language that does not make promissory commitments. Documented rationale: legal liability outweighs tone preference.

### B.2 Financial Services: Investment Research Assistant

**Enterprise constraints:**
- `O(sox_audit_trail)` — all AI-generated content used in financial reporting must have complete audit trail
- `F(investment_advice_to_retail)` — prompts must not instruct the model to provide personalized investment advice to retail investors
- `O(data_source_citation)` — prompts must instruct the model to cite data sources for all quantitative claims

**Department constraints (Institutional Research):**
- Inherits all enterprise constraints
- `P_meta(investment_thesis_language)` — the department has considered investment thesis language and does not restrict it for professional audiences
- `O(model_uncertainty_disclosure)` — prompts must instruct the model to disclose uncertainty and confidence levels

**Project constraints (Analyst Tool):**
- Inherits all enterprise and department constraints
- `F(forward_looking_statements_without_disclaimer)` — prompts must prohibit forward-looking statements unless accompanied by required regulatory disclaimers

**Decomposition example: F(investment_advice_to_retail)**

Semantic intent: "The AI system must not provide personalized investment recommendations to retail investors."

Mechanical components: audience detection (prompt must include explicit audience scoping: "this tool is for institutional investors only"), keyword patterns for personalized advice language ("you should buy," "I recommend," "based on your portfolio"), required disclaimer presence.

Procedural components: compliance officer review, quarterly re-attestation, FINRA suitability framework review.

Residual gap: mechanical checks cannot distinguish between general market commentary (permitted) and implicit investment guidance that sophisticated readers would recognize as advice but retail investors might act on differently. Compensating control: 25% HITL review by compliance, with escalation protocol for borderline cases.

### B.3 Energy: Grid Operations Decision Support

**Enterprise constraints:**
- `O(nerc_cip_compliance_notice)` — all AI interactions involving grid operations must include NERC CIP compliance framing
- `F(operational_commands_without_human_confirmation)` — prompts must never instruct the model to issue operational commands; all recommendations require human confirmation
- `O(audit_all_recommendations)` — every AI recommendation must be fully auditable with complete constraint evaluation

**Department constraints (Grid Operations):**
- `O(cite_sensor_data_source)` — prompts must instruct the model to cite specific sensor data sources for all recommendations
- `F(recommendations_outside_trained_scenarios)` — prompts must restrict the model to scenarios covered by validated training data

**Conflict example: efficiency vs safety**

An efficiency optimization constraint `O(minimize_response_time)` from the Operations Efficiency domain creates tension with `F(operational_commands_without_human_confirmation)` from the Safety domain. Faster response times could pressure the system toward fewer confirmation steps.

Resolution: Enterprise Precedence Table specifies Safety > Efficiency as the default domain priority. The safety prohibition takes absolute precedence. The efficiency constraint is modified to: `O(minimize_response_time_within_safety_constraints)` — a new atomic property that explicitly acknowledges the safety boundary.

**Break-glass scenario:** During an active grid stability event, an enterprise-level actor invokes break-glass to add `F(non_essential_ai_queries)` — temporarily prohibiting all non-essential AI interactions to preserve computational resources for critical grid operations. TTL: 24 hours (energy sector default). The constraint is tightening-only (adds a prohibition). Immediate CONTRADICTION scan confirms no conflicts with existing constraints. Post-hoc review within 24 hours formalizes the constraint through standard dual-control if the operational condition persists, or allows automatic reversion if the event resolves.

---

## Appendix C: Constraint Library Template

### C.1 Library Structure

A constraint library package contains:

**Library metadata:**
- Name and version (semantic versioning: major.minor.patch)
- Target industry and regulatory basis (e.g., "HIPAA Privacy Rule §164.502-§164.514")
- Maintainer organization and contact
- Last review date and next scheduled review
- Applicable governance levels (recommended)

**Constraint entries:** each containing:
- Deontic modality (O or F)
- Atomic property identifier from the controlled vocabulary
- Recommended governance level (enterprise, department, or project)
- Domain assignment
- Evaluability class (C_m or C_p)
- Rationale and regulatory reference

**Pre-attested decompositions:** for each semantic intent underlying the constraints:
- The intent record (C_s) with full semantic description
- The mechanical components (C_m) with implementation specifications
- The procedural components (C_p) with attestation requirements
- The decomposition attestation record (who attested, when, qualifications)

**Known residual gaps:** documented per constraint where soundness cannot be fully demonstrated, including compensating control recommendations.

**Regression test suite:** known-bad inputs that mechanical checks must catch, plus expected evaluation outcomes. This suite is the minimum test set that must pass during local verification (§9.4).

### C.2 Library Lifecycle

Libraries follow semantic versioning:

- **Major version** (e.g., 1.0 → 2.0): new constraints added, constraints removed, or constraint semantics materially changed. Requires full re-attestation by adopting organizations.
- **Minor version** (e.g., 1.0 → 1.1): modified decompositions, updated mechanical checks, expanded test suites. Requires regression test re-execution by adopting organizations.
- **Patch version** (e.g., 1.0.0 → 1.0.1): documentation updates, test case additions, editorial corrections. No action required by adopting organizations beyond acknowledgment.

Each version requires domain expert attestation from the library maintainer. Organizations that have adopted a library version receive update notifications when new versions are released, including a changelog and impact assessment.

### C.3 Example: HIPAA Prompt Constraints v1.0 (Skeleton)

**Library metadata:**
- Name: HIPAA Prompt Constraints
- Version: 1.0.0
- Industry: Healthcare / Life Sciences
- Regulatory basis: HIPAA Privacy Rule (45 CFR §164.502-§164.514), HIPAA Security Rule (45 CFR §164.302-§164.318)
- Recommended level: Enterprise

**Constraint entries (8 constraints):**

| # | Modality | Property | Class | Regulatory Reference |
|---|----------|----------|-------|---------------------|
| 1 | F | phi_disclosure_in_output | C_m | §164.502(a) — Minimum Necessary |
| 2 | O | hipaa_disclaimer_present | C_m | §164.520 — Notice of Privacy Practices |
| 3 | F | pii_in_system_logs | C_m | §164.312(a) — Access Control |
| 4 | O | data_minimization_instruction | C_m | §164.502(b) — Minimum Necessary Standard |
| 5 | F | medical_diagnosis_instruction | C_m + C_p | §164.502 — Uses and Disclosures |
| 6 | O | patient_redirect_to_provider | C_m | §164.502 — Treatment Exception |
| 7 | F | phi_in_test_environments | C_m | §164.308(a)(4) — Information Access Management |
| 8 | O | breach_notification_instruction | C_p | §164.404 — Notification to Individuals |

**Decomposition example (Constraint #1: F(phi_disclosure_in_output)):**

Intent record: "The AI system must not include protected health information in its outputs unless the disclosure is explicitly authorized under a HIPAA-permitted use or disclosure."

Mechanical components:
- NER-based PHI detection in prompt instructions (names, dates, MRNs, SSNs, addresses, phone numbers)
- Pattern-based detection of prompt instructions that request PHI inclusion
- Required presence of explicit "Do not include patient-identifying information" instruction

Procedural components:
- Privacy officer review and attestation that the prompt's PHI protections are adequate for the intended use case
- Annual re-attestation aligned with HIPAA risk assessment cycle

Residual gap: mechanical NER detection covers approximately 90% of standard PHI patterns. Novel identifiers, contextual PHI (e.g., "the patient in room 3"), and inference-based re-identification are not fully captured. Compensating control: 10% HITL review of outputs by trained privacy staff.

**Regression test suite (partial):**
- 50 known-PHI test prompts containing explicit patient identifiers → mechanical checks must flag all 50
- 20 boundary-case prompts with contextual identifiers → mechanical checks should flag at least 15
- 30 clean prompts with no PHI → mechanical checks must pass all 30 (false positive baseline)

---

*End of White Paper 1: Enterprise Prompt Governance*
