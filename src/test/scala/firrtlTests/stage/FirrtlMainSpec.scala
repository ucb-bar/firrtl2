// SPDX-License-Identifier: Apache-2.0

package firrtlTests.stage

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, PrintWriter}
import firrtl2.{BuildInfo, FileUtils}
import firrtl2.stage.{FirrtlMain, WarnNoScalaVersionDeprecation}
import firrtl2.util.BackendCompilationUtilities._
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import firrtl2.options.StageOptionsView

/** Testing for the top-level [[FirrtlStage]] via [[FirrtlMain]].
  *
  * This test uses the [[org.scalatest.FeatureSpec FeatureSpec]] intentionally as this test exercises the top-level
  * interface and is more suitable to an Acceptance Testing style.
  */
class FirrtlMainSpec extends AnyFeatureSpec with GivenWhenThen with Matchers with firrtl2.testutils.Utils {

  /** Parameterizes one test of [[FirrtlMain]]. Running the [[FirrtlMain]] `main` with certain args should produce
    * certain files and not produce others.
    * @param args arguments to pass
    * @param circuit a [[FirrtlCircuitFixture]] to use. This will generate an appropriate '-i $targetDir/$main.fi'
    * argument.
    * @param files expected files that will be created
    * @param files files that should NOT be created
    * @param stdout expected stdout string, None if no output expected
    * @param stderr expected stderr string, None if no output expected
    * @param result expected exit code
    */
  case class FirrtlMainTest(
    args:     Array[String],
    circuit:  Option[FirrtlCircuitFixture] = Some(new SimpleFirrtlCircuitFixture),
    files:    Seq[String] = Seq.empty,
    notFiles: Seq[String] = Seq.empty,
    stdout:   Option[String] = None,
    stderr:   Option[String] = None,
    result:   Int = 0) {

    /** Generate a name for the test based on the arguments */
    def testName: String = "args" + args.mkString("_")

    /** Print the arguments as a single string */
    def argsString: String = args.mkString(" ")
  }

  /** Run the FIRRTL stage with some command line arguments expecting output files to be created. The target directory is
    * implied, but will be created by the stage.
    * @param p some test parameters
    */
  def runStageExpectFiles(p: FirrtlMainTest): Unit = {
    Scenario(s"""User runs FIRRTL Stage with '${p.argsString}'""") {
      val f = new FirrtlMainFixture
      val td = new TargetDirectoryFixture(p.testName)

      val inputFile: Array[String] = p.circuit match {
        case Some(c) =>
          And("some input FIRRTL IR")
          val in = new File(td.dir, c.main)
          val pw = new PrintWriter(in)
          pw.write(c.input)
          pw.close()
          Array("-i", in.toString)
        case None => Array.empty
      }

      p.files.foreach(f => new File(td.buildDir.getAbsolutePath + s"/$f").delete())
      p.notFiles.foreach(f => new File(td.buildDir.getAbsolutePath + s"/$f").delete())

      When(s"""the user tries to compile with '${p.argsString}'""")
      val (stdout, stderr, result) =
        grabStdOutErr { f.stage.mainInner(inputFile ++ Array("-td", td.buildDir.toString) ++ p.args) }

      p.stdout match {
        case Some(a) =>
          Then(s"""STDOUT should include "$a"""")
          stdout should include(a)
        case None =>
          Then(s"nothing should print to STDOUT")
          stdout should be(empty)
      }

      p.stderr match {
        case Some(a) =>
          And(s"""STDERR should include "$a"""")
          stderr should include(a)
        case None =>
          And(s"nothing should print to STDERR")
          stderr should be(empty)
      }

      p.result match {
        case 0 =>
          And(s"the exit code should be 0")
          result shouldBe 0
        case a =>
          And(s"the exit code should be $a")
          result shouldBe a
      }

      p.files.foreach { f =>
        And(s"file '$f' should be emitted in the target directory")
        val out = new File(td.buildDir.getAbsolutePath + s"/$f")
        out should (exist)
      }

      p.notFiles.foreach { f =>
        And(s"file '$f' should NOT be emitted in the target directory")
        val out = new File(td.buildDir.getAbsolutePath + s"/$f")
        out should not(exist)
      }
    }
  }

  /** Test fixture that links to the [[FirrtlMain]] object. This could be done without, but its use matches the
    * Given/When/Then style more accurately.
    */
  class FirrtlMainFixture {
    Given("the FIRRTL stage")
    val stage = FirrtlMain
  }

  /** Test fixture that creates a build directory
    * @param dirName the name of the base directory; a `build` directory is created under this
    */
  class TargetDirectoryFixture(dirName: String) {
    val dir = new File(s"test_run_dir/FirrtlMainSpec/$dirName")
    val buildDir = new File(dir.getAbsolutePath + "/build")
    dir.mkdirs()
  }

  trait FirrtlCircuitFixture {
    val main:  String
    val input: String
  }

  /** Test fixture defining a simple FIRRTL circuit that will emit differently with and without `--split-modules`. */
  class SimpleFirrtlCircuitFixture extends FirrtlCircuitFixture {
    val main: String = "Top"
    val input: String =
      """|circuit Top:
         |  module Top:
         |    output foo: UInt<32>
         |    inst c of Child
         |    inst e of External
         |    foo <= tail(add(c.foo, e.foo), 1)
         |  module Child:
         |    output foo: UInt<32>
         |    inst e of External
         |    foo <= e.foo
         |  extmodule External:
         |    output foo: UInt<32>
         |""".stripMargin
  }

  info("As a FIRRTL command line user")
  info("I want to compile some FIRRTL")
  Feature("FirrtlMain command line interface") {
    Scenario("User tries to discover available options") {
      val f = new FirrtlMainFixture

      When("the user passes '--help'")
      val (out, _, result) = grabStdOutErr { f.stage.mainInner(Array("--help")) }

      Then("the usage text should be shown")
      out should include("Usage: firrtl")

      And("usage text should show known registered transforms")
      out should include("--no-dce")

      And("usage text should show known registered libraries")
      out should include("MemLib Options")

      info("""And the exit code should be 0, but scopt catches all throwable, so we can't check this... ¯\_(ツ)_/¯""")
      // And("the exit code should be zero")
      // out should be (Left(0))
    }

    Seq(
      /* Test all standard emitters with and without annotation file outputs */
      FirrtlMainTest(args = Array("-X", "none", "-E", "chirrtl"), files = Seq("Top.fir")),
      FirrtlMainTest(args = Array("-X", "mhigh", "-E", "mhigh"), files = Seq("Top.mhi.fir")),
      FirrtlMainTest(args = Array("-X", "high", "-E", "high"), files = Seq("Top.hi.fir")),
      FirrtlMainTest(
        args = Array("-X", "middle", "-E", "middle", "-foaf", "Top"),
        files = Seq("Top.mid.fir", "Top.anno.json")
      ),
      FirrtlMainTest(
        args = Array("-X", "low", "-E", "low", "-foaf", "annotations.anno.json"),
        files = Seq("Top.lo.fir", "annotations.anno.json")
      ),
      FirrtlMainTest(
        args = Array("-X", "verilog", "-E", "verilog", "-foaf", "foo.anno"),
        files = Seq("Top.v", "foo.anno.anno.json")
      ),
      FirrtlMainTest(
        args = Array("-X", "sverilog", "-E", "sverilog", "-foaf", "foo.json"),
        files = Seq("Top.sv", "foo.json.anno.json")
      ),
      /* Test all one file per module emitters */
      FirrtlMainTest(args = Array("-X", "none", "-e", "chirrtl"), files = Seq("Top.fir", "Child.fir")),
      FirrtlMainTest(
        args = Array("-X", "mhigh", "-e", "mhigh"),
        files = Seq("Top.mhi.fir", "Child.mhi.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "high", "-e", "high"),
        files = Seq("Top.hi.fir", "Child.hi.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "middle", "-e", "middle"),
        files = Seq("Top.mid.fir", "Child.mid.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "low", "-e", "low"),
        files = Seq("Top.lo.fir", "Child.lo.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "verilog", "-e", "verilog"),
        files = Seq("Top.v", "Child.v")
      ),
      FirrtlMainTest(
        args = Array("-X", "sverilog", "-e", "sverilog"),
        files = Seq("Top.sv", "Child.sv")
      ),
      /* Test mixing of -E with -e */
      FirrtlMainTest(
        args = Array("-X", "middle", "-E", "high", "-e", "middle"),
        files = Seq("Top.hi.fir", "Top.mid.fir", "Child.mid.fir"),
        notFiles = Seq("Child.hi.fir")
      ),
      /* Test changes to output file name */
      FirrtlMainTest(args = Array("-X", "none", "-E", "chirrtl", "-o", "foo"), files = Seq("foo.fir")),
      FirrtlMainTest(
        args = Array("-X", "mhigh", "-E", "mhigh", "-o", "foo"),
        files = Seq("foo.mhi.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "high", "-E", "high", "-o", "foo"),
        files = Seq("foo.hi.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "middle", "-E", "middle", "-o", "foo.middle"),
        files = Seq("foo.middle.mid.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "low", "-E", "low", "-o", "foo.lo.fir"),
        files = Seq("foo.lo.fir")
      ),
      FirrtlMainTest(
        args = Array("-X", "verilog", "-E", "verilog", "-o", "foo.sv"),
        files = Seq("foo.sv.v")
      ),
      FirrtlMainTest(
        args = Array("-X", "sverilog", "-E", "sverilog", "-o", "Foo"),
        files = Seq("Foo.sv")
      ),
      /* Test that an output is generated if no emitter is specified */
      FirrtlMainTest(
        args = Array("-X", "verilog", "-o", "Foo"),
        files = Seq("Foo.v")
      )
    )
      .foreach(runStageExpectFiles)

    Scenario("User doesn't specify a target directory") {
      val f = new FirrtlMainFixture

      When("the user doesn't specify a target directory")
      val outName = "FirrtlMainSpecNoTargetDirectory"
      val out = new File(s"$outName.hi.fir")
      out.delete()
      val result = f.stage.mainInner(
        Array("-i", "src/test/resources/integration/GCDTester.fir", "-o", outName, "-X", "high", "-E", "high")
      )

      Then("outputs should be written to current directory")
      out should (exist)
      out.delete()

      And("the exit code should be 0")
      result shouldBe 0
    }
  }

  info("As a FIRRTL command line user")
  info("I want to receive error messages when I do not specify mandatory inputs")
  Feature("FirrtlMain input validation of mandatory options") {
    Scenario("User gives no command line options (no input circuit specified)") {
      val f = new FirrtlMainFixture

      When("the user passes no arguments")
      val (out, err, result) = grabStdOutErr { f.stage.mainInner(Array.empty) }

      Then("an error should be printed on stdout")
      out should include(s"Error: Unable to determine FIRRTL source to read")

      And("no usage text should be shown")
      (out should not).include("Usage: firrtl")

      And("nothing should print to stderr")
      err should be(empty)

      And("the exit code should be 1")
      result should be(1)
    }
  }

  info("As a FIRRTL command line user")
  info("I want to receive helpful error and warnings message")
  Feature("FirrtlMain input validation") {
    /* Note: most input validation occurs inside firrtl2.stage.phases.Checks. This seeks to validate command line
     * behavior.
     */

    Seq(
      /* Erroneous inputs */
      FirrtlMainTest(
        args = Array("--thisIsNotASupportedOption"),
        circuit = None,
        stdout = Some("Error: Unknown option"),
        result = 1
      ),
      FirrtlMainTest(
        args = Array("-i", "foo", "--info-mode", "Use"),
        circuit = None,
        stdout = Some("Unknown info mode 'Use'! (Did you misspell it?)"),
        result = 1
      ),
      FirrtlMainTest(
        args = Array("-i", "test_run_dir/I-DO-NOT-EXIST"),
        circuit = None,
        stdout = Some("Input file 'test_run_dir/I-DO-NOT-EXIST' not found!"),
        result = 1
      ),
      FirrtlMainTest(
        args = Array("-i", "foo", "-X", "Verilog"),
        circuit = None,
        stdout = Some("Unknown compiler name 'Verilog'! (Did you misspell it?)"),
        result = 1
      )
    )
      .foreach(runStageExpectFiles)

  }

  info("As a FIRRTL transform developer")
  info("I want to register my custom transforms with FIRRTL")
  Feature("FirrtlMain transform registration") {
    Scenario("User doesn't know if their transforms were registered") {
      val f = new FirrtlMainFixture

      When("the user passes '--show-registrations'")
      val (out, _, result) = grabStdOutErr { f.stage.mainInner(Array("--show-registrations")) }

      Then("stdout should show registered transforms")
      out should include("firrtl2.passes.InlineInstances")

      And("stdout should show registered libraries")
      out should include("firrtl2.passes.memlib.MemLibOptions")

      And("the exit code should be 1")
      result should be(1)
    }
  }

  info("As a longtime FIRRTL user")
  info("I migrate from Driver to FirrtlMain")
  Feature("FirrtlMain migration helpers") {
    def optionRemoved(a: String): Option[String] = Some(s"Option '$a' was removed as part of the FIRRTL Stage refactor")
    Seq(
      /* Removed --top-name/-tn handling */
      FirrtlMainTest(
        args = Array("--top-name", "foo"),
        circuit = None,
        stdout = optionRemoved("--top-name/-tn"),
        result = 1
      ),
      FirrtlMainTest(args = Array("-tn"), circuit = None, stdout = optionRemoved("--top-name/-tn"), result = 1),
      /* Removed --split-modules/-fsm handling */
      FirrtlMainTest(
        args = Array("--split-modules"),
        circuit = None,
        stdout = optionRemoved("--split-modules/-fsm"),
        result = 1
      ),
      FirrtlMainTest(args = Array("-fsm"), circuit = None, stdout = optionRemoved("--split-modules/-fsm"), result = 1)
    )
      .foreach(runStageExpectFiles)
  }

}
