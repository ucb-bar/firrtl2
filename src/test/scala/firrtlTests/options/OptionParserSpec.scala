// SPDX-License-Identifier: Apache-2.0

package firrtlTests.options

import firrtl2.AnnotationSeq
import firrtl2.annotations.{Annotation, NoTargetAnnotation}
import firrtl2.options.{DoNotTerminateOnExit, DuplicateHandling, ExceptOnError, OptionsException}

import scopt.OptionParser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OptionParserSpec extends AnyFlatSpec with Matchers with firrtl2.testutils.Utils {

  case class IntAnnotation(x: Int) extends NoTargetAnnotation {
    def extract: Int = x
  }

  /* An option parser that prepends to a Seq[Int] */
  class IntParser extends OptionParser[AnnotationSeq]("Int Parser") {
    opt[Int]("integer").abbr("n").unbounded().action((x, c) => IntAnnotation(x) +: c)
    help("help")
  }

  trait DuplicateShortOption { this: OptionParser[AnnotationSeq] =>
    opt[Int]("not-an-integer").abbr("n").unbounded().action((x, c) => IntAnnotation(x) +: c)
  }

  trait DuplicateLongOption { this: OptionParser[AnnotationSeq] =>
    opt[Int]("integer").abbr("m").unbounded().action((x, c) => IntAnnotation(x) +: c)
  }

  var terminateStatus: Either[String, Unit] = null
  trait TerminateToVariable[T] extends OptionParser[T] {
    override def terminate(exitState: Either[String, Unit]): Unit = terminateStatus = exitState
  }

  trait WithIntParser {
    val parser = new IntParser
  }

  trait WithIntTerminateCaptureParser {
    val parser = new IntParser with TerminateToVariable[AnnotationSeq]
  }

  behavior.of("A default OptionsParser")

  it should "call sys.exit if terminate is called" in new WithIntTerminateCaptureParser {
    info("exit status of 1 for failure")
    parser.terminate(Left("some message"))
    terminateStatus should be(Left("some message"))

    info("exit status of 0 for success")
    parser.terminate(Right(()))
    terminateStatus should be(Right(()))
  }

  it should "print to stderr on an invalid option" in new WithIntParser {
    grabStdOutErr { parser.parse(Array("--foo"), Seq[Annotation]()) }._2 should include("Unknown option --foo")
  }

  behavior.of("An OptionParser with DoNotTerminateOnExit mixed in")

  it should "disable sys.exit for terminate method" in {
    val parser = new IntParser with DoNotTerminateOnExit[AnnotationSeq]

    // parser.terminate() should never call sys.exit(), if it does, then the JVM will terminate
    info("no exit for failure")
    parser.terminate(Left("some message"))

    info("no exit for success")
    parser.terminate(Right(()))
  }

  behavior.of("An OptionParser with DuplicateHandling mixed in")

  it should "detect short duplicates" in {
    val parser = new IntParser with DuplicateHandling with DuplicateShortOption
    intercept[OptionsException] { parser.parse(Array[String](), Seq[Annotation]()) }.getMessage should startWith(
      "Duplicate short option"
    )
  }

  it should "detect long duplicates" in {
    val parser = new IntParser with DuplicateHandling with DuplicateLongOption
    intercept[OptionsException] { parser.parse(Array[String](), Seq[Annotation]()) }.getMessage should startWith(
      "Duplicate long option"
    )
  }

  behavior.of("An OptionParser with ExceptOnError mixed in")

  it should "cause an OptionsException on an invalid option" in {
    val parser = new IntParser with ExceptOnError[AnnotationSeq]
    intercept[OptionsException] { parser.parse(Array("--foo"), Seq[Annotation]()) }.getMessage should include(
      "Unknown option"
    )
  }

}
