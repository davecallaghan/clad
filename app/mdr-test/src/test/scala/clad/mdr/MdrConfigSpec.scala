package clad.mdr

import clad.monitoring.AlertSeverity
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path, Paths}

class MdrConfigSpec extends AnyFunSuite:
  /** personal_finance's runtime.yaml, copied as a fixture. */
  private val adopter: Path = Paths.get(getClass.getResource("/runtime.yaml").toURI)

  private def variant(edit: String => String): Path =
    val f = Files.createTempFile("runtime", ".yaml"); Files.writeString(f, edit(Files.readString(adopter))); f

  test("personal_finance's runtime.yaml loads, every number from the file") {
    val c = MdrConfig.load(adopter).fold(fail(_), identity)
    assert(c.intervalSeconds == 60 && c.inFlightGraceSeconds == 120 && c.injectionAlertCount == 3)
    assert(c.temperatureMax == 1.0 && c.maxTokensMax == 4000 && c.reviewCadenceDays == 90)
    assert(c.dueMinutes == Map(AlertSeverity.P1Critical -> 15L, AlertSeverity.P2High -> 60L, AlertSeverity.P3Medium -> 240L,
      AlertSeverity.P4Low -> 1440L))
  }

  test("a missing mdr.interval_seconds is refused with the key's name") {
    val r = MdrConfig.load(variant(_.replace("  interval_seconds: 60\n", "")))
    assert(r.left.exists(_.contains("mdr.interval_seconds")), r)
  }

  test("severity_due_minutes without P4 is refused") {
    val r = MdrConfig.load(variant(_.replaceAll("(?m)^  P4: .*$", "")))
    assert(r.left.exists(_.contains("severity_due_minutes.P4")), r)
  }

  test("the environment: every required variable is named when missing; the webhook is optional") {
    val env = Map("CLAD_MDR_CONFIG_DIR" -> "/c", "CLAD_MDR_GATEWAY_DATA_DIR" -> "/g", "CLAD_MDR_DATA_DIR" -> "/d",
      "CLAD_MDR_KEYS_DIR" -> "/k", "CLAD_MDR_EGRESS_LOG" -> "/e/access.log", "CLAD_MDR_LISTENERS" -> "8081=api.deepinfra.com",
      "CLAD_MDR_DB_URL" -> "jdbc:postgresql://x/pf")
    val e = MdrEnv.from(env).fold(fail(_), identity)
    assert(e.listeners == Map("8081" -> "api.deepinfra.com") && e.webhookUrl.isEmpty)
    assert(MdrEnv.from(env - "CLAD_MDR_DB_URL") == Left("CLAD_MDR_DB_URL is not set"))
    assert(MdrEnv.from(env + ("CLAD_MDR_WEBHOOK_URL" -> "http://hook")).exists(_.webhookUrl.contains("http://hook")))
  }
