package clad.gateway.store

import java.lang.reflect.{InvocationTargetException, Proxy}
import java.sql.{Connection, SQLException}
import org.scalatest.funsuite.AnyFunSuite

class JdbcSpec extends AnyFunSuite:
  private def fake(failing: Set[String]): Connection =
    Proxy.newProxyInstance(getClass.getClassLoader, Array(classOf[Connection]), (_, m, _) =>
      if failing.contains(m.getName) then throw SQLException(s"${m.getName} failed", "XX000")
      else if m.getReturnType == classOf[Boolean] then java.lang.Boolean.FALSE
      else null).asInstanceOf[Connection]

  test("a rollback failure does not replace the original exception") {
    val e = intercept[SQLException](Jdbc.transacted(fake(Set("rollback")))(_ => throw SQLException("original", "22000")))
    assert(e.getMessage == "original")
    assert(e.getSuppressed.exists(_.getMessage == "rollback failed"))
  }

  test("a close failure does not mask f's error, and alone becomes a Left") {
    val masked = Jdbc.using(fake(Set("close")))(_ => throw SQLException("original", "22000"))
    assert(masked.left.exists(_.contains("original")))
    val alone = Jdbc.using(fake(Set("close")))(_ => 1)
    assert(alone.left.exists(_.contains("close failed")))
    assert(Jdbc.using(fake(Set.empty))(_ => 1) == Right(1))
  }
