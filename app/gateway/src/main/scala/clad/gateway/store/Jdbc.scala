package clad.gateway.store

import java.sql.{Connection, DriverManager, SQLException}

object Jdbc:
  private def describe(e: SQLException): String = s"${e.getSQLState}: ${e.getMessage}"

  def withConnection[A](url: String)(f: Connection => A): Either[String, A] =
    try using(DriverManager.getConnection(url))(f)
    catch case e: SQLException => Left(describe(e))

  def withTransaction[A](url: String)(f: Connection => A): Either[String, A] =
    withConnection(url)(c => transacted(c)(f))

  /** Runs f on c and closes c. A close failure is suppressed onto f's exception when there is one; when f
    * succeeded and only close fails, the close failure is the Left. */
  private[store] def using[A](c: Connection)(f: Connection => A): Either[String, A] =
    val primary: Either[SQLException, A] =
      try Right(f(c))
      catch
        case e: SQLException => Left(e)
        case e: Throwable =>
          try c.close()
          catch case x: SQLException => e.addSuppressed(x)
          throw e
    try
      c.close()
      primary.left.map(describe)
    catch
      case x: SQLException =>
        primary match
          case Left(e) =>
            e.addSuppressed(x)
            Left(describe(e))
          case Right(_) => Left(describe(x))

  /** Runs f in a transaction on c. A rollback failure is suppressed onto the original exception, which is rethrown. */
  private[store] def transacted[A](c: Connection)(f: Connection => A): A =
    c.setAutoCommit(false)
    try
      val a = f(c)
      c.commit()
      a
    catch
      case e: SQLException =>
        try c.rollback()
        catch case r: SQLException => e.addSuppressed(r)
        throw e
