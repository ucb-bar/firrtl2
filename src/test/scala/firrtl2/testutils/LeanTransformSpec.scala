// SPDX-License-Identifier: Apache-2.0

package firrtl2.testutils

import firrtl2.logger.LazyLogging
import firrtl2.{ir, AnnotationSeq, CircuitState, EmitCircuitAnnotation, Transform}
import firrtl2.options.Dependency
import firrtl2.passes.RemoveEmpty
import firrtl2.stage.TransformManager.TransformDependency
import org.scalatest.flatspec.AnyFlatSpec

class VerilogTransformSpec(others: Seq[TransformDependency] = Seq())
    extends LeanTransformSpec(others :+ Dependency[firrtl2.VerilogEmitter])
class MinimumVerilogTransformSpec extends LeanTransformSpec(Seq(Dependency[firrtl2.MinimumVerilogEmitter]))
class SystemVerilogTransformSpec(others: Seq[TransformDependency] = Seq())
    extends LeanTransformSpec(others :+ Dependency[firrtl2.SystemVerilogEmitter])
class LowFirrtlTransformSpec(others: Seq[TransformDependency] = Seq())
    extends LeanTransformSpec(others :+ Dependency[firrtl2.LowFirrtlEmitter])
class MidFirrtlTransformSpec(others: Seq[TransformDependency] = Seq())
    extends LeanTransformSpec(others :+ Dependency[firrtl2.MiddleFirrtlEmitter])
class HighFirrtlTransformSpec(others: Seq[TransformDependency] = Seq())
    extends LeanTransformSpec(others :+ Dependency[firrtl2.HighFirrtlEmitter])

/** The new cool kid on the block, creates a custom compiler for your transform. */
class LeanTransformSpec(protected val transforms: Seq[TransformDependency])
    extends AnyFlatSpec
    with FirrtlMatchers
    with LazyLogging {
  private val compiler = new firrtl2.stage.transforms.Compiler(transforms)
  private val emitterAnnos = LeanTransformSpec.deriveEmitCircuitAnnotations(transforms)
  private val emitsVerilog = emitterAnnos.map(_.emitter.getName).exists(n => n.endsWith("VerilogEmitter"))

  protected def compile(src: String): CircuitState = compile(src, Seq())
  protected def compile(src: String, annos: AnnotationSeq): CircuitState = compile(firrtl2.Parser.parse(src), annos)
  protected def compile(c:   ir.Circuit): CircuitState = compile(c, Seq())
  protected def compile(c:   ir.Circuit, annos: AnnotationSeq): CircuitState =
    compiler.transform(CircuitState(c, emitterAnnos ++ annos))
  protected def execute(input: String, check: String): CircuitState = execute(input, check, Seq())
  protected def execute(input: String, check: String, unordered: Boolean): CircuitState =
    execute(input, check, Seq(), Seq(), unordered = unordered)
  protected def execute(input: String, check: String, inAnnos: AnnotationSeq): CircuitState =
    execute(input, check, inAnnos, Seq())
  protected def execute(input: String, check: String, inAnnos: AnnotationSeq, checkAnnos: AnnotationSeq): CircuitState =
    execute(input, check, inAnnos, checkAnnos, unordered = false)
  protected def execute(
    input:      String,
    check:      String,
    inAnnos:    AnnotationSeq,
    checkAnnos: AnnotationSeq,
    unordered:  Boolean
  ): CircuitState = {
    val finalState = compile(parse(input), inAnnos)
    val emittedCircuit = finalState.getEmittedCircuit.value

    val actual = normalizeCode(emittedCircuit, verilogNotFirrtl = emitsVerilog)
    val expected = normalizeCode(check, verilogNotFirrtl = emitsVerilog)

    // check circuit
    logger.debug("Actual Circuit")
    logger.debug(actual)
    logger.debug("Expected Circuit")
    logger.debug(expected)
    if (unordered) {
      val actualSet = Set(actual.split("\n"): _*)
      val expectedSet = Set(expected.split("\n"): _*)
      assert(actualSet == expectedSet, f"$actual\nVS\n$expected")
    } else {
      actual should be(expected)
    }

    // check annotations
    if (checkAnnos.nonEmpty) {
      logger.debug("Actual Output Annotations")
      val actualAnnos: AnnotationSeq = finalState.annotations
      actualAnnos.foreach { anno => logger.debug(anno.serialize) }
      logger.debug("Expected Output Annotations")
      checkAnnos.foreach { anno => logger.debug(anno.serialize) }
      checkAnnos.foreach { check => actualAnnos should contain(check) }
    }

    // return compilation result
    finalState
  }
  private def normalizeCode(src: String, verilogNotFirrtl: Boolean): String = if (verilogNotFirrtl) {
    val lines = src.split("\n")
    lines.map(normalized).mkString("\n")
  } else {
    val circuit = parse(src)
    RemoveEmpty.run(circuit).serialize
  }
  protected def removeSkip(c: ir.Circuit): ir.Circuit = {
    def onStmt(s: ir.Statement): ir.Statement = s.mapStmt(onStmt)
    c.mapModule(m => m.mapStmt(onStmt))
  }
}

private object LeanTransformSpec {
  private def deriveEmitCircuitAnnotations(transforms: Iterable[TransformDependency]): Seq[EmitCircuitAnnotation] =
    MakeCompiler.deriveEmitCircuitAnnotations(transforms.map(_.getObject()))
}

/** Use this if you just need to create a standard compiler and want to save some typing. */
trait MakeCompiler {
  def makeVerilogCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.VerilogEmitter]) ++ transforms)
  def makeSystemVerilogCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.SystemVerilogEmitter]) ++ transforms)
  def makeMinimumVerilogCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.MinimumVerilogEmitter]) ++ transforms)
  def makeMiddleFirrtlCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.MiddleFirrtlEmitter]) ++ transforms)
  def makeLowFirrtlCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.LowFirrtlEmitter]) ++ transforms)
  def makeHighFirrtlCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.HighFirrtlEmitter]) ++ transforms)
  def makeEmitVerilogCircuitAnno:   EmitCircuitAnnotation = EmitCircuitAnnotation(classOf[firrtl2.VerilogEmitter])
  def makeEmitLowFirrtlCircuitAnno: EmitCircuitAnnotation = EmitCircuitAnnotation(classOf[firrtl2.LowFirrtlEmitter])
  def deriveEmitCircuitAnnotations(compiler: firrtl2.stage.transforms.Compiler): Seq[EmitCircuitAnnotation] =
    deriveEmitCircuitAnnotations(compiler.transformOrder)
  def deriveEmitCircuitAnnotations(transforms: Iterable[Transform]): Seq[EmitCircuitAnnotation] = {
    // generate the correct emit circuit annotations depending on the emitter we are using
    val emitters = transforms.collect { case e: firrtl2.Emitter => e }
    emitters.map(e => EmitCircuitAnnotation(e.getClass)).toSeq
  }
}

object MakeCompiler extends MakeCompiler {}
