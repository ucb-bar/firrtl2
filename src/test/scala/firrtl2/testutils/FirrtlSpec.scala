// SPDX-License-Identifier: Apache-2.0

package firrtl2.testutils

import java.io._
import scala.sys.process._
import org.scalatest._
import org.scalatestplus.scalacheck._
import firrtl2._
import firrtl2.ir._
import firrtl2.Parser.UseInfo
import firrtl2.options.Dependency
import firrtl2.stage.{FirrtlFileAnnotation, Forms, InfoModeAnnotation, RunFirrtlTransformAnnotation}
import firrtl2.analyses.{GetNamespace, ModuleNamespaceAnnotation}
import firrtl2.annotations._
import firrtl2.logger.{LazyLogging, LogLevel, LogLevelAnnotation}
import firrtl2.transforms.{DontTouchAnnotation, NoDedupAnnotation, RenameModules}
import firrtl2.renamemap.MutableRenameMap
import firrtl2.util.BackendCompilationUtilities
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec

class CheckLowForm extends SeqTransform {
  // it only makes sense to check the LowFirrtl after we convert the circuit.
  override def prerequisites = Forms.LowForm
  def transforms = Seq(
    passes.CheckHighForm
  )
}

case class RenameTopAnnotation(newTopName: String) extends NoTargetAnnotation

object RenameTop extends Transform {
  override def invalidates(a: Transform) = false

  override val optionalPrerequisites = Seq(Dependency[RenameModules])

  override val optionalPrerequisiteOf = Seq(Dependency[VerilogEmitter], Dependency[MinimumVerilogEmitter])

  def execute(state: CircuitState): CircuitState = {
    val c = state.circuit
    val ns = Namespace(c)

    val newTopName = state.annotations
      .collectFirst({
        case RenameTopAnnotation(name) =>
          require(ns.tryName(name))
          name
      })
      .getOrElse(c.main)

    state.annotations.collect {
      case ModuleNamespaceAnnotation(mustNotCollideNS) => require(mustNotCollideNS.tryName(newTopName))
    }

    val modulesx = c.modules.map {
      case m: Module if (m.name == c.main) => m.copy(name = newTopName)
      case m => m
    }

    val renames = MutableRenameMap()
    renames.record(CircuitTarget(c.main), CircuitTarget(newTopName))
    state.copy(circuit = c.copy(main = newTopName, modules = modulesx), renames = Some(renames))
  }
}

object Equivalence {
  import firrtl2.util.BackendCompilationUtilities.createTestDirectory

  /** Extra transforms to run by default */
  val extraCheckTransforms = Seq(Dependency[CheckLowForm])

  /** Check equivalence of Firrtl transforms using yosys
    *
    * @param input             string containing Firrtl source
    * @param customTransforms  Firrtl transforms to test for equivalence
    * @param customAnnotations Optional Firrtl annotations
    * @param timesteps         the maximum number of timesteps to consider
    */
  def firrtlEquivalenceTest(
    input:             String,
    customTransforms:  Seq[Dependency[Transform]] = Seq.empty,
    customAnnotations: AnnotationSeq = Seq.empty,
    timesteps:         Int = 1
  ): Unit = {
    val circuit = Parser.parse(input)
    val prefix = circuit.main
    val testDir = createTestDirectory(prefix + "_equivalence_test")

    def toAnnos(xforms: Seq[Dependency[Transform]]) = xforms.map(RunFirrtlTransformAnnotation(_))

    def getBaseAnnos(topName: String) = {
      val baseTransforms = Dependency(RenameTop) +: extraCheckTransforms
      firrtl2.options.TargetDirAnnotation(testDir.toString) +:
        InfoModeAnnotation("ignore") +:
        RenameTopAnnotation(topName) +:
        stage.FirrtlCircuitAnnotation(circuit) +:
        stage.RunFirrtlTransformAnnotation.stringToEmitter("mverilog") +:
        stage.OutputFileAnnotation(topName) +:
        toAnnos(baseTransforms)
    }

    val customName = s"${prefix}_custom"
    val customAnnos =
      customAnnotations ++: toAnnos((Dependency[GetNamespace]) +: customTransforms) ++: getBaseAnnos(customName)

    val customResult = (new firrtl2.stage.FirrtlStage).execute(Array.empty, customAnnos)
    val nsAnno = customResult.collectFirst { case m: ModuleNamespaceAnnotation => m }.get

    val refSuggestedName = s"${prefix}_ref"
    val refAnnos = Seq(RunFirrtlTransformAnnotation(new RenameModules), nsAnno) ++: getBaseAnnos(refSuggestedName)

    val refResult = (new firrtl2.stage.FirrtlStage).execute(Array.empty, refAnnos)
    val refName =
      refResult.collectFirst({ case stage.FirrtlCircuitAnnotation(c) => c.main }).getOrElse(refSuggestedName)

    assert(BackendCompilationUtilities.yosysExpectSuccess(customName, refName, testDir, timesteps))
  }

  /** Check equivalence of Firrtl with reference Verilog
    *
    * @note the name of the reference Verilog module is grabbed via regex
    * @param inputFirrtl      string containing Firrtl source
    * @param referenceVerilog Verilog that will be used as reference for LEC
    * @param timesteps        the maximum number of timesteps to consider
    */
  def firrtlEquivalenceWithVerilog(
    inputFirrtl:      String,
    referenceVerilog: String,
    timesteps:        Int = 1
  ): Unit = {
    val VerilogModule = """(?s).*module\s(\w+).*""".r
    val refName = referenceVerilog match {
      case VerilogModule(name) => name
      case _                   => throw new Exception(s"Reference Verilog must match simple regex! $VerilogModule")
    }
    val circuit = Parser.parse(inputFirrtl)
    val inputName = circuit.main
    require(refName != inputName, s"Name of reference Verilog must not match name of input FIRRTL: $refName")

    val testDir = createTestDirectory(inputName + "_equivalence_test")

    val annos = List(
      firrtl2.options.TargetDirAnnotation(testDir.toString),
      InfoModeAnnotation("ignore"),
      stage.FirrtlCircuitAnnotation(circuit),
      stage.RunFirrtlTransformAnnotation.stringToEmitter("verilog"),
      stage.OutputFileAnnotation(inputName)
    )

    (new firrtl2.stage.FirrtlStage).execute(Array(), annos)

    // Write reference
    val w = new FileWriter(new File(testDir, s"$refName.v"))
    w.write(referenceVerilog)
    w.close()

    assert(BackendCompilationUtilities.yosysExpectSuccess(inputName, refName, testDir, timesteps))
  }
}

trait FirrtlRunners {
  import BackendCompilationUtilities._

  val cppHarnessResourceName: String = "/firrtl2/testTop.cpp"

  /** Extra transforms to run by default */
  val extraCheckTransforms = Seq(Dependency[CheckLowForm])

  /** Compiles input Firrtl to Verilog */
  def compileToVerilog(input: String, annotations: AnnotationSeq = Seq.empty): String = {
    compileToVerilogCircuitState(input, annotations).getEmittedCircuit.value
  }

  /** Compiles input Firrtl to Verilog */
  def compileToVerilogCircuitState(input: String, annotations: AnnotationSeq = Seq.empty): CircuitState = {
    val circuit = Parser.parse(input)
    val compiler = MakeCompiler.makeVerilogCompiler(extraCheckTransforms)
    compiler.transform(CircuitState(circuit, MakeCompiler.makeEmitVerilogCircuitAnno +: annotations))
  }

  /** Run Verilator lint on some Verilog text
    *
    * @param inputVerilog Verilog to pass to `verilator --lint-only`
    * @return Verilator return 0
    */
  def lintVerilog(inputVerilog: String): Unit = {
    val testDir = createTestDirectory(s"${this.getClass.getSimpleName}_lint")
    val filename = new File(testDir, "test.v")
    val w = new FileWriter(filename)
    w.write(inputVerilog)
    w.close()

    val cmd = Seq("verilator", "--lint-only", filename.toString)
    assert(cmd.!(loggingProcessLogger) == 0, "Lint must pass")
  }

  /** Compile a Firrtl file
    *
    * @param prefix is the name of the Firrtl file without path or file extension
    * @param srcDir directory where all Resources for this test are located
    * @param annotations Optional Firrtl annotations
    */
  def compileFirrtlTest(
    prefix:           String,
    srcDir:           String,
    customTransforms: Seq[Dependency[Transform]] = Seq.empty,
    annotations:      AnnotationSeq = Seq.empty
  ): File = {
    val testDir = createTestDirectory(prefix)
    val inputFile = new File(testDir, s"${prefix}.fir")
    copyResourceToFile(s"${srcDir}/${prefix}.fir", inputFile)

    val annos =
      FirrtlFileAnnotation(inputFile.toString) +:
        firrtl2.options.TargetDirAnnotation(testDir.toString) +:
        InfoModeAnnotation("ignore") +:
        annotations ++:
        (customTransforms ++ extraCheckTransforms).map(RunFirrtlTransformAnnotation(_))

    (new firrtl2.stage.FirrtlStage).execute(Array.empty, annos)

    testDir
  }

  /** Execute a Firrtl Test
    *
    * @param prefix is the name of the Firrtl file without path or file extension
    * @param srcDir directory where all Resources for this test are located
    * @param verilogPrefixes names of option Verilog resources without path or file extension
    * @param annotations Optional Firrtl annotations
    */
  def runFirrtlTest(
    prefix:           String,
    srcDir:           String,
    verilogPrefixes:  Seq[String] = Seq.empty,
    customTransforms: Seq[Dependency[Transform]] = Seq.empty,
    annotations:      AnnotationSeq = Seq.empty
  ) = {
    val testDir = compileFirrtlTest(prefix, srcDir, customTransforms, annotations)
    val harness = new File(testDir, s"top.cpp")
    copyResourceToFile(cppHarnessResourceName, harness)

    // Note file copying side effect
    val verilogFiles = verilogPrefixes.map { vprefix =>
      val file = new File(testDir, s"$vprefix.v")
      copyResourceToFile(s"$srcDir/$vprefix.v", file)
      file
    }

    verilogToCpp(prefix, testDir, verilogFiles, harness) #&&
      cppToExe(prefix, testDir) !
      loggingProcessLogger
    assert(executeExpectingSuccess(prefix, testDir))
  }
}

object FirrtlRunners extends FirrtlRunners {}

trait FirrtlMatchers extends Matchers {
  def dontTouch(ref: ReferenceTarget): DontTouchAnnotation = {
    DontTouchAnnotation(ref)
  }
  def dontTouch(path: String): Annotation = {
    val parts = path.split('.')
    require(parts.size >= 2, "Must specify both module and component!")
    val name = ComponentName(parts.tail.mkString("."), ModuleName(parts.head, CircuitName("Top")))
    DontTouchAnnotation(name)
  }
  def dontDedup(mod: String): Annotation = {
    require(mod.split('.').size == 1, "Can only specify a Module, not a component or instance")
    NoDedupAnnotation(ModuleName(mod, CircuitName("Top")))
  }
  // Replace all whitespace with a single space and remove leading and
  //   trailing whitespace
  // Note this is intended for single-line strings, no newlines
  def normalized(s: String): String = {
    require(!s.contains("\n"))
    s.replaceAll("\\s+", " ").trim
  }

  /** Helper to make circuits that are the same appear the same */
  def canonicalize(circuit: Circuit): Circuit = {
    import firrtl2.Mappers._
    def onModule(mod: DefModule) = mod.map(firrtl2.Utils.squashEmpty)
    circuit.map(onModule)
  }
  def parse(str: String) = Parser.parse(str)
}

object FirrtlCheckers extends FirrtlMatchers {
  import matchers._
  implicit class TestingFunctionsOnCircuitState(val state: CircuitState) extends AnyVal {
    def search(pf: PartialFunction[Any, Boolean]): Boolean = state.circuit.search(pf)
  }
  implicit class TestingFunctionsOnCircuit(val circuit: Circuit) extends AnyVal {
    def search(pf: PartialFunction[Any, Boolean]): Boolean = {
      val f = pf.lift
      def rec(node: Any): Boolean = {
        f(node) match {
          // If the partial function is defined on this node, return its result
          case Some(res) => res
          // Otherwise keep digging
          case None =>
            require(
              node.isInstanceOf[Product] || !node.isInstanceOf[FirrtlNode],
              "Error! Unexpected FirrtlNode that does not implement Product!"
            )
            val iter = node match {
              case p: Product       => p.productIterator
              case i: Iterable[Any] => i.iterator
              case _ => Iterator.empty
            }
            iter.foldLeft(false) {
              case (res, elt) => if (res) res else rec(elt)
            }
        }
      }
      rec(circuit)
    }
  }

  /** Checks that the emitted circuit has the expected line, both will be normalized */
  def containLine(expectedLine: String) = containLines(expectedLine)

  /** Checks that the emitted circuit contains the expected lines contiguously and in order;
    * all lines will be normalized
    */
  def containLines(expectedLines: String*) = new CircuitStateStringsMatcher(expectedLines)

  class CircuitStateStringsMatcher(expectedLines: Seq[String]) extends Matcher[CircuitState] {
    override def apply(state: CircuitState): MatchResult = {
      val emitted = state.getEmittedCircuit.value
      MatchResult(
        emitted.split("\n").map(normalized).containsSlice(expectedLines.map(normalized)),
        emitted + "\n did not contain \"" + expectedLines + "\"",
        s"${state.circuit.main} contained $expectedLines"
      )
    }
  }

  def containTree(pf: PartialFunction[Any, Boolean]) = new CircuitStatePFMatcher(pf)

  class CircuitStatePFMatcher(pf: PartialFunction[Any, Boolean]) extends Matcher[CircuitState] {
    override def apply(state: CircuitState): MatchResult = {
      MatchResult(
        state.search(pf),
        state.circuit.serialize + s"\n did not contain $pf",
        s"${state.circuit.main} contained $pf"
      )
    }
  }
}

abstract class FirrtlPropSpec extends AnyPropSpec with ScalaCheckPropertyChecks with FirrtlRunners with LazyLogging

abstract class FirrtlFlatSpec extends AnyFlatSpec with FirrtlRunners with FirrtlMatchers with LazyLogging

// Who tests the testers?
class TestFirrtlFlatSpec extends FirrtlFlatSpec {
  import FirrtlCheckers._

  val c = parse("""
                  |circuit Test:
                  |  module Test :
                  |    input in : UInt<8>
                  |    output out : UInt<8>
                  |    out <= in
                  |""".stripMargin)
  val state = CircuitState(c, Seq(MakeCompiler.makeEmitLowFirrtlCircuitAnno))
  val compiled = MakeCompiler.makeLowFirrtlCompiler().transform(state)

  // While useful, ScalaTest helpers should be used over search
  behavior.of("Search")

  it should "be supported on Circuit" in {
    assert(c.search {
      case Connect(_, Reference("out", _, _, _), Reference("in", _, _, _)) => true
    })
  }
  it should "be supported on CircuitStates" in {
    assert(state.search {
      case Connect(_, Reference("out", _, _, _), Reference("in", _, _, _)) => true
    })
  }
  it should "be supported on the results of compilers" in {
    assert(compiled.search {
      case Connect(_, WRef("out", _, _, _), WRef("in", _, _, _)) => true
    })
  }

  // Use these!!!
  behavior.of("ScalaTest helpers")

  they should "work for lines of emitted text" in {
    compiled should containLine(s"input in : UInt<8>")
    compiled should containLine(s"output out : UInt<8>")
    compiled should containLine(s"out <= in")
  }

  they should "work for partial functions matching on subtrees" in {
    val UInt8 = UIntType(IntWidth(8)) // BigInt unapply is weird
    compiled should containTree { case Port(_, "in", Input, UInt8) => true }
    compiled should containTree { case Port(_, "out", Output, UInt8) => true }
    compiled should containTree { case Connect(_, WRef("out", _, _, _), WRef("in", _, _, _)) => true }
  }
}

/** Super class for execution driven Firrtl tests */
abstract class ExecutionTest(
  name:        String,
  dir:         String,
  vFiles:      Seq[String] = Seq.empty,
  annotations: AnnotationSeq = Seq.empty)
    extends FirrtlPropSpec {
  property(s"$name should execute correctly") {
    runFirrtlTest(name, dir, vFiles, annotations = annotations)
  }
}

/** Super class for execution driven Firrtl tests compiled without optimizations */
abstract class ExecutionTestNoOpt(
  name:        String,
  dir:         String,
  vFiles:      Seq[String] = Seq.empty,
  annotations: AnnotationSeq = Seq.empty)
    extends ExecutionTest(name, dir, vFiles, RunFirrtlTransformAnnotation(new MinimumVerilogEmitter) +: annotations)

/** Super class for compilation driven Firrtl tests */
abstract class CompilationTest(name: String, dir: String) extends FirrtlPropSpec {
  property(s"$name should compile correctly") {
    compileFirrtlTest(name, dir)
  }
}

trait Utils {

  /** Run some Scala thunk and return STDOUT and STDERR as strings.
    * @param thunk some Scala code
    * @return a tuple containing STDOUT, STDERR, and what the thunk returns
    */
  def grabStdOutErr[T](thunk: => T): (String, String, T) = {
    val stdout, stderr = new ByteArrayOutputStream()
    val ret = scala.Console.withOut(stdout) { scala.Console.withErr(stderr) { thunk } }
    (stdout.toString, stderr.toString, ret)
  }
}

/** Super class for equivalence driven Firrtl tests */
abstract class EquivalenceTest(transforms: Seq[Dependency[Transform]], name: String, dir: String)
    extends FirrtlFlatSpec {
  import Equivalence.firrtlEquivalenceTest
  val fileName = s"$dir/$name.fir"
  val in = getClass.getResourceAsStream(fileName)
  if (in == null) {
    throw new FileNotFoundException(s"Resource '$fileName'")
  }
  val source = scala.io.Source.fromInputStream(in)
  val input =
    try source.mkString
    finally source.close()

  s"$name with ${transforms.map(_.getName).mkString(", ")}" should
    s"be equivalent to $name without ${transforms.map(_.getName).mkString(", ")}" in {
    firrtlEquivalenceTest(input, transforms)
  }
}
