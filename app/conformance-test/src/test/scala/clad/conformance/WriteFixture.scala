package clad.conformance

object WriteFixture:
  def main(args: Array[String]): Unit =
    val b = TestBundles.valid()
    println(b.dir)
