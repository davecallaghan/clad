package clad.integrity

import clad.integrity.test.InMemoryInteractionLog
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.time.Instant
import scala.util.Success

class InteractionLogSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  var log: InMemoryInteractionLog = _

  override def beforeEach(): Unit =
    log = new InMemoryInteractionLog()
    log.clear()

  test("InteractionId.generate produces unique IDs"):
    val id1 = InteractionId.generate()
    val id2 = InteractionId.generate()
    id1 should not be id2

  test("InteractionId stores and retrieves value"):
    val id = InteractionId("test-id")
    id.value shouldBe "test-id"

  test("register adds entry"):
    val entry = GilEntry(
      interactionId = InteractionId("test-1"),
      registeredAt = Instant.now()
    )
    log.register(entry) shouldBe Success(())
    log.allEntries should contain(entry)

  test("count increments after register"):
    log.count shouldBe Success(0)
    log.register(GilEntry(InteractionId("test-1"), Instant.now()))
    log.count shouldBe Success(1)
    log.register(GilEntry(InteractionId("test-2"), Instant.now()))
    log.count shouldBe Success(2)

  test("exists returns true for registered ID"):
    val id = InteractionId("test-1")
    log.register(GilEntry(id, Instant.now()))
    log.exists(id) shouldBe Success(true)

  test("exists returns false for unregistered ID"):
    val id = InteractionId("nonexistent")
    log.exists(id) shouldBe Success(false)

  test("entriesBetween covers all entries in window"):
    val start = Instant.parse("2024-01-01T00:00:00Z")
    val middle1 = Instant.parse("2024-01-02T00:00:00Z")
    val middle2 = Instant.parse("2024-01-03T00:00:00Z")
    val end = Instant.parse("2024-01-04T00:00:00Z")

    val entry1 = GilEntry(InteractionId("test-1"), middle1)
    val entry2 = GilEntry(InteractionId("test-2"), middle2)

    log.register(entry1)
    log.register(entry2)

    val result = log.entriesBetween(start, end)
    result shouldBe Success(Vector(entry1, entry2))

  test("entriesBetween covers partial entries in window"):
    val beforeWindow = Instant.parse("2024-01-01T00:00:00Z")
    val start = Instant.parse("2024-01-02T00:00:00Z")
    val inWindow = Instant.parse("2024-01-03T00:00:00Z")
    val end = Instant.parse("2024-01-04T00:00:00Z")
    val afterWindow = Instant.parse("2024-01-05T00:00:00Z")

    val entry1 = GilEntry(InteractionId("test-1"), beforeWindow)
    val entry2 = GilEntry(InteractionId("test-2"), inWindow)
    val entry3 = GilEntry(InteractionId("test-3"), afterWindow)

    log.register(entry1)
    log.register(entry2)
    log.register(entry3)

    val result = log.entriesBetween(start, end)
    result shouldBe Success(Vector(entry2))

  test("entriesBetween covers none when window excludes all"):
    val entryTime = Instant.parse("2024-01-01T00:00:00Z")
    val start = Instant.parse("2024-01-02T00:00:00Z")
    val end = Instant.parse("2024-01-03T00:00:00Z")

    val entry = GilEntry(InteractionId("test-1"), entryTime)
    log.register(entry)

    val result = log.entriesBetween(start, end)
    result shouldBe Success(Vector.empty)

  test("GilEntry carries optional metadata"):
    val metadata = Map("key1" -> "value1", "key2" -> "value2")
    val entry = GilEntry(
      interactionId = InteractionId("test-1"),
      registeredAt = Instant.now(),
      metadata = metadata
    )
    entry.metadata shouldBe metadata

  test("GilEntry has empty metadata by default"):
    val entry = GilEntry(
      interactionId = InteractionId("test-1"),
      registeredAt = Instant.now()
    )
    entry.metadata shouldBe Map.empty

  test("InteractionLog has no update method"):
    val methods = classOf[InteractionLog[?]].getDeclaredMethods.map(_.getName)
    methods should not contain "update"

  test("InteractionLog has no delete method"):
    val methods = classOf[InteractionLog[?]].getDeclaredMethods.map(_.getName)
    methods should not contain "delete"

  test("InteractionLog has no remove method"):
    val methods = classOf[InteractionLog[?]].getDeclaredMethods.map(_.getName)
    methods should not contain "remove"
