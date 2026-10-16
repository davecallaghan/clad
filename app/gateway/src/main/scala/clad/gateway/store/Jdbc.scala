package clad.gateway.store

import java.sql.{Connection, DriverManager, SQLException}

object Jdbc:
  def withConnection[A](url: String)(f: Connection => A): Either[String, A] =
    try
      val c = DriverManager.getConnection(url)
      try Right(f(c)) finally c.close()
    catch case e: SQLException => Left(s"${e.getSQLState}: ${e.getMessage}")

  def withTransaction[A](url: String)(f: Connection => A): Either[String, A] =
    withConnection(url) { c =>
      c.setAutoCommit(false)
      try
        val a = f(c)
        c.commit()
        a
      catch
        case e: SQLException =>
          c.rollback()
          throw e
    }
