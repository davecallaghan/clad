package clad.integrity.test

class FailingEngine(failureMessage: String = "Simulated engine failure"):
  private var callCount = 0
  private var failOnCall: Option[Int] = None

  def failOnNthCall(n: Int): Unit = failOnCall = Some(n)
  def alwaysFail(): Unit = failOnCall = Some(1)

  def evaluate[A](result: => A): A =
    callCount += 1
    failOnCall match
      case Some(n) if callCount >= n =>
        throw RuntimeException(failureMessage)
      case _ => result
