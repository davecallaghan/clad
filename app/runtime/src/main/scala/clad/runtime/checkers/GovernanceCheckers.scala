package clad.runtime.checkers

import clad.core.*
import clad.evaluation.*

object GovernanceCheckers:

  val phiAccessLogging: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("phi_access_logging"),
    MetadataRequirement.KeyEquals("audit_logging", "enabled")
  )

  val minimumNecessaryData: PropertyChecker = RegexChecker(
    PropertyId.unsafe("minimum_necessary_data"),
    Seq(
      "\\d{3}-\\d{2}-\\d{4}".r,
      "\\d{2}/\\d{2}/\\d{4}".r,
      "MRN\\s*:?\\s*\\d+".r,
      "[A-Z]\\d{2}\\.\\d{1,2}".r
    ),
    MatchMode.Any
  )

  val humanReviewRequired: PropertyChecker =
    val clinicalKeywords = KeywordChecker(
      PropertyId.unsafe("_hr_keywords"),
      Set("recommend", "prescribe", "diagnose", "treatment", "diagnosis"),
      threshold = 1
    )
    val noReviewCompleted = new PropertyChecker:
      val propertyId: PropertyId = PropertyId.unsafe("_hr_no_review")
      def check(artifact: PromptArtifact): Boolean =
        !artifact.metadata.get("human_review").contains("completed")
    CompositeChecker(
      PropertyId.unsafe("human_review_required"),
      Seq(clinicalKeywords, noReviewCompleted),
      MatchMode.All
    )

  val encryptionInTransit: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("encryption_in_transit"),
    MetadataRequirement.AnyOf(Seq(
      MetadataRequirement.KeyEquals("transport", "tls"),
      MetadataRequirement.KeyEquals("transport", "https")
    ))
  )

  val baaCurrent: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("baa_current"),
    MetadataRequirement.KeyEquals("baa_status", "current")
  )

  val modelDocumentationCurrent: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("model_documentation_current"),
    MetadataRequirement.AllOf(Seq(
      MetadataRequirement.KeyEquals("model_card", "present"),
      MetadataRequirement.KeyExists("last_validated")
    ))
  )

  val biasAssessmentCompleted: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("bias_assessment_completed"),
    MetadataRequirement.KeyEquals("bias_assessment", "completed")
  )

  val consentDocumented: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("consent_documented"),
    MetadataRequirement.KeyEquals("patient_consent", "documented")
  )

  val piiDetected: PropertyChecker = RegexChecker(
    PropertyId.unsafe("pii_detected"),
    Seq(
      "\\S+@\\S+\\.\\S+".r,
      "\\d{3}[-.]?\\d{3}[-.]?\\d{4}".r,
      "\\d{3}-\\d{2}-\\d{4}".r
    ),
    MatchMode.Any
  )

  val clinicalValidationCurrent: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("clinical_validation_current"),
    MetadataRequirement.KeyEquals("clinical_validation", "current")
  )

  val adverseEventReporting: PropertyChecker = StructuralChecker(
    PropertyId.unsafe("adverse_event_reporting"),
    MetadataRequirement.KeyEquals("adverse_event_process", "active")
  )

  val decisionExplainability: PropertyChecker = KeywordChecker(
    PropertyId.unsafe("decision_explainability"),
    Set("because", "based on", "reasoning", "rationale", "evidence suggests"),
    threshold = 2
  )

  val all: Seq[PropertyChecker] = Seq(
    phiAccessLogging, minimumNecessaryData, humanReviewRequired,
    encryptionInTransit, baaCurrent, modelDocumentationCurrent,
    biasAssessmentCompleted, consentDocumented, piiDetected,
    clinicalValidationCurrent, adverseEventReporting, decisionExplainability
  )

  def registry: Either[CheckerRegistry.DuplicateChecker, CheckerRegistry] =
    CheckerRegistry.build(all)
