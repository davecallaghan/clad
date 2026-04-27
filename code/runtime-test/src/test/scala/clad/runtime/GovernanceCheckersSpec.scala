package clad.runtime

import clad.core.*
import clad.evaluation.*
import clad.runtime.checkers.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GovernanceCheckersSpec extends AnyFlatSpec with Matchers:

  "GovernanceCheckers.all" should "contain exactly 12 checkers" in {
    GovernanceCheckers.all should have size 12
  }

  "GovernanceCheckers.registry" should "build without duplicate checkers" in {
    GovernanceCheckers.registry shouldBe a [Right[_, _]]
  }

  // phi_access_logging tests
  "phiAccessLogging checker" should "detect audit_logging=enabled" in {
    val artifact = PromptArtifact(
      content = "Patient data access log",
      metadata = Map("audit_logging" -> "enabled")
    )
    GovernanceCheckers.phiAccessLogging.check(artifact) shouldBe true
  }

  it should "not detect when audit_logging is missing" in {
    val artifact = PromptArtifact(
      content = "Patient data access log",
      metadata = Map.empty
    )
    GovernanceCheckers.phiAccessLogging.check(artifact) shouldBe false
  }

  it should "not detect when audit_logging has wrong value" in {
    val artifact = PromptArtifact(
      content = "Patient data access log",
      metadata = Map("audit_logging" -> "disabled")
    )
    GovernanceCheckers.phiAccessLogging.check(artifact) shouldBe false
  }

  // minimum_necessary_data tests
  "minimumNecessaryData checker" should "detect SSN pattern" in {
    val artifact = PromptArtifact(
      content = "Patient SSN: 123-45-6789 for reference"
    )
    GovernanceCheckers.minimumNecessaryData.check(artifact) shouldBe true
  }

  it should "detect date of birth pattern" in {
    val artifact = PromptArtifact(
      content = "DOB: 01/15/1990"
    )
    GovernanceCheckers.minimumNecessaryData.check(artifact) shouldBe true
  }

  it should "detect MRN pattern" in {
    val artifact = PromptArtifact(
      content = "MRN: 123456"
    )
    GovernanceCheckers.minimumNecessaryData.check(artifact) shouldBe true
  }

  it should "detect ICD code pattern" in {
    val artifact = PromptArtifact(
      content = "Diagnosis code: E11.9"
    )
    GovernanceCheckers.minimumNecessaryData.check(artifact) shouldBe true
  }

  it should "not detect clean content without PII" in {
    val artifact = PromptArtifact(
      content = "General medical information without identifiers"
    )
    GovernanceCheckers.minimumNecessaryData.check(artifact) shouldBe false
  }

  // human_review_required tests
  "humanReviewRequired checker" should "detect clinical keywords without human review" in {
    val artifact = PromptArtifact(
      content = "I recommend this treatment for the patient",
      metadata = Map.empty
    )
    GovernanceCheckers.humanReviewRequired.check(artifact) shouldBe true
  }

  it should "not detect when human_review is completed" in {
    val artifact = PromptArtifact(
      content = "I recommend this treatment for the patient",
      metadata = Map("human_review" -> "completed")
    )
    GovernanceCheckers.humanReviewRequired.check(artifact) shouldBe false
  }

  it should "not detect without clinical keywords" in {
    val artifact = PromptArtifact(
      content = "General information about medical procedures",
      metadata = Map.empty
    )
    GovernanceCheckers.humanReviewRequired.check(artifact) shouldBe false
  }

  it should "detect multiple clinical keywords" in {
    val artifact = PromptArtifact(
      content = "I prescribe this medication and diagnose the condition",
      metadata = Map.empty
    )
    GovernanceCheckers.humanReviewRequired.check(artifact) shouldBe true
  }

  // encryption_in_transit tests
  "encryptionInTransit checker" should "detect transport=tls" in {
    val artifact = PromptArtifact(
      content = "Data transmission",
      metadata = Map("transport" -> "tls")
    )
    GovernanceCheckers.encryptionInTransit.check(artifact) shouldBe true
  }

  it should "detect transport=https" in {
    val artifact = PromptArtifact(
      content = "Data transmission",
      metadata = Map("transport" -> "https")
    )
    GovernanceCheckers.encryptionInTransit.check(artifact) shouldBe true
  }

  it should "not detect transport=http" in {
    val artifact = PromptArtifact(
      content = "Data transmission",
      metadata = Map("transport" -> "http")
    )
    GovernanceCheckers.encryptionInTransit.check(artifact) shouldBe false
  }

  it should "not detect when transport is missing" in {
    val artifact = PromptArtifact(
      content = "Data transmission",
      metadata = Map.empty
    )
    GovernanceCheckers.encryptionInTransit.check(artifact) shouldBe false
  }

  // baa_current tests
  "baaCurrent checker" should "detect baa_status=current" in {
    val artifact = PromptArtifact(
      content = "BAA agreement",
      metadata = Map("baa_status" -> "current")
    )
    GovernanceCheckers.baaCurrent.check(artifact) shouldBe true
  }

  it should "not detect baa_status=expired" in {
    val artifact = PromptArtifact(
      content = "BAA agreement",
      metadata = Map("baa_status" -> "expired")
    )
    GovernanceCheckers.baaCurrent.check(artifact) shouldBe false
  }

  it should "not detect when baa_status is missing" in {
    val artifact = PromptArtifact(
      content = "BAA agreement",
      metadata = Map.empty
    )
    GovernanceCheckers.baaCurrent.check(artifact) shouldBe false
  }

  // model_documentation_current tests
  "modelDocumentationCurrent checker" should "detect both model_card and last_validated" in {
    val artifact = PromptArtifact(
      content = "Model documentation",
      metadata = Map(
        "model_card" -> "present",
        "last_validated" -> "2024-01-15"
      )
    )
    GovernanceCheckers.modelDocumentationCurrent.check(artifact) shouldBe true
  }

  it should "not detect when model_card is absent" in {
    val artifact = PromptArtifact(
      content = "Model documentation",
      metadata = Map("last_validated" -> "2024-01-15")
    )
    GovernanceCheckers.modelDocumentationCurrent.check(artifact) shouldBe false
  }

  it should "not detect when last_validated is absent" in {
    val artifact = PromptArtifact(
      content = "Model documentation",
      metadata = Map("model_card" -> "present")
    )
    GovernanceCheckers.modelDocumentationCurrent.check(artifact) shouldBe false
  }

  it should "not detect when model_card has wrong value" in {
    val artifact = PromptArtifact(
      content = "Model documentation",
      metadata = Map(
        "model_card" -> "missing",
        "last_validated" -> "2024-01-15"
      )
    )
    GovernanceCheckers.modelDocumentationCurrent.check(artifact) shouldBe false
  }

  // bias_assessment_completed tests
  "biasAssessmentCompleted checker" should "detect bias_assessment=completed" in {
    val artifact = PromptArtifact(
      content = "Bias assessment",
      metadata = Map("bias_assessment" -> "completed")
    )
    GovernanceCheckers.biasAssessmentCompleted.check(artifact) shouldBe true
  }

  it should "not detect bias_assessment=pending" in {
    val artifact = PromptArtifact(
      content = "Bias assessment",
      metadata = Map("bias_assessment" -> "pending")
    )
    GovernanceCheckers.biasAssessmentCompleted.check(artifact) shouldBe false
  }

  it should "not detect when bias_assessment is missing" in {
    val artifact = PromptArtifact(
      content = "Bias assessment",
      metadata = Map.empty
    )
    GovernanceCheckers.biasAssessmentCompleted.check(artifact) shouldBe false
  }

  // consent_documented tests
  "consentDocumented checker" should "detect patient_consent=documented" in {
    val artifact = PromptArtifact(
      content = "Patient consent form",
      metadata = Map("patient_consent" -> "documented")
    )
    GovernanceCheckers.consentDocumented.check(artifact) shouldBe true
  }

  it should "not detect patient_consent=not_obtained" in {
    val artifact = PromptArtifact(
      content = "Patient consent form",
      metadata = Map("patient_consent" -> "not_obtained")
    )
    GovernanceCheckers.consentDocumented.check(artifact) shouldBe false
  }

  it should "not detect when patient_consent is missing" in {
    val artifact = PromptArtifact(
      content = "Patient consent form",
      metadata = Map.empty
    )
    GovernanceCheckers.consentDocumented.check(artifact) shouldBe false
  }

  // pii_detected tests
  "piiDetected checker" should "detect email address" in {
    val artifact = PromptArtifact(
      content = "Contact john@example.com for more info"
    )
    GovernanceCheckers.piiDetected.check(artifact) shouldBe true
  }

  it should "detect phone number" in {
    val artifact = PromptArtifact(
      content = "Call 555-123-4567 for assistance"
    )
    GovernanceCheckers.piiDetected.check(artifact) shouldBe true
  }

  it should "detect phone number with dots" in {
    val artifact = PromptArtifact(
      content = "Phone: 555.123.4567"
    )
    GovernanceCheckers.piiDetected.check(artifact) shouldBe true
  }

  it should "detect SSN" in {
    val artifact = PromptArtifact(
      content = "SSN: 123-45-6789"
    )
    GovernanceCheckers.piiDetected.check(artifact) shouldBe true
  }

  it should "not detect clean content without PII" in {
    val artifact = PromptArtifact(
      content = "This is general information without any personal identifiers"
    )
    GovernanceCheckers.piiDetected.check(artifact) shouldBe false
  }

  // clinical_validation_current tests
  "clinicalValidationCurrent checker" should "detect clinical_validation=current" in {
    val artifact = PromptArtifact(
      content = "Clinical validation report",
      metadata = Map("clinical_validation" -> "current")
    )
    GovernanceCheckers.clinicalValidationCurrent.check(artifact) shouldBe true
  }

  it should "not detect clinical_validation=expired" in {
    val artifact = PromptArtifact(
      content = "Clinical validation report",
      metadata = Map("clinical_validation" -> "expired")
    )
    GovernanceCheckers.clinicalValidationCurrent.check(artifact) shouldBe false
  }

  it should "not detect when clinical_validation is missing" in {
    val artifact = PromptArtifact(
      content = "Clinical validation report",
      metadata = Map.empty
    )
    GovernanceCheckers.clinicalValidationCurrent.check(artifact) shouldBe false
  }

  // adverse_event_reporting tests
  "adverseEventReporting checker" should "detect adverse_event_process=active" in {
    val artifact = PromptArtifact(
      content = "Adverse event reporting process",
      metadata = Map("adverse_event_process" -> "active")
    )
    GovernanceCheckers.adverseEventReporting.check(artifact) shouldBe true
  }

  it should "not detect adverse_event_process=inactive" in {
    val artifact = PromptArtifact(
      content = "Adverse event reporting process",
      metadata = Map("adverse_event_process" -> "inactive")
    )
    GovernanceCheckers.adverseEventReporting.check(artifact) shouldBe false
  }

  it should "not detect when adverse_event_process is missing" in {
    val artifact = PromptArtifact(
      content = "Adverse event reporting process",
      metadata = Map.empty
    )
    GovernanceCheckers.adverseEventReporting.check(artifact) shouldBe false
  }

  // decision_explainability tests
  "decisionExplainability checker" should "detect 2+ explainability markers" in {
    val artifact = PromptArtifact(
      content = "This decision is made because the evidence suggests a positive outcome"
    )
    GovernanceCheckers.decisionExplainability.check(artifact) shouldBe true
  }

  it should "detect based on and rationale" in {
    val artifact = PromptArtifact(
      content = "The rationale for this is based on clinical guidelines"
    )
    GovernanceCheckers.decisionExplainability.check(artifact) shouldBe true
  }

  it should "not detect with only 1 marker" in {
    val artifact = PromptArtifact(
      content = "This is because of the situation"
    )
    GovernanceCheckers.decisionExplainability.check(artifact) shouldBe false
  }

  it should "not detect with no markers" in {
    val artifact = PromptArtifact(
      content = "This is a simple statement without explanation"
    )
    GovernanceCheckers.decisionExplainability.check(artifact) shouldBe false
  }

  it should "be case-insensitive" in {
    val artifact = PromptArtifact(
      content = "BECAUSE the REASONING shows clear EVIDENCE SUGGESTS improvement"
    )
    GovernanceCheckers.decisionExplainability.check(artifact) shouldBe true
  }
