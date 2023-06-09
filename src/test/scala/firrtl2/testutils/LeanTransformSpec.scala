// SPDX-License-Identifier: Apache-2.0

package firrtl2.testutils

import firrtl2.logger.LazyLogging
import firrtl2.{ir, AnnotationSeq, CircuitState, EmitCircuitAnnotation}
import firrtl2.options.Dependency
import firrtl2.passes.RemoveEmpty
import firrtl2.stage.TransformManager.TransformDependency
import org.scalatest.flatspec.AnyFlatSpec

class VerilogTransformSpec extends LeanTransformSpec(Seq(Dependency[firrtl2.VerilogEmitter]))
class LowFirrtlTransformSpec extends LeanTransformSpec(Seq(Dependency[firrtl2.LowFirrtlEmitter]))

/** The new cool kid on the block, creates a custom compiler for your transform. */
class LeanTransformSpec(protected val transforms: Seq[TransformDependency])
    extends AnyFlatSpec
    with FirrtlMatchers
    with LazyLogging {
  private val compiler = new firrtl2.stage.transforms.Compiler(transforms)
  private val emitterAnnos = LeanTransformSpec.deriveEmitCircuitAnnotations(transforms)

  protected def compile(src: String): CircuitState = compile(src, Seq())
  protected def compile(src: String, annos: AnnotationSeq): CircuitState = compile(firrtl2.Parser.parse(src), annos)
  protected def compile(c:   ir.Circuit): CircuitState = compile(c, Seq())
  protected def compile(c:   ir.Circuit, annos: AnnotationSeq): CircuitState =
    compiler.transform(CircuitState(c, emitterAnnos ++ annos))
  protected def execute(input: String, check: String): CircuitState = execute(input, check, Seq())
  protected def execute(input: String, check: String, inAnnos: AnnotationSeq): CircuitState = {
    val finalState = compiler.transform(CircuitState(parse(input), inAnnos))
    val actual = RemoveEmpty.run(parse(finalState.getEmittedCircuit.value)).serialize
    val expected = parse(check).serialize
    logger.debug(actual)
    logger.debug(expected)
    actual should be(expected)
    finalState
  }
  protected def removeSkip(c: ir.Circuit): ir.Circuit = {
    def onStmt(s: ir.Statement): ir.Statement = s.mapStmt(onStmt)
    c.mapModule(m => m.mapStmt(onStmt))
  }
}

private object LeanTransformSpec {
  private def deriveEmitCircuitAnnotations(transforms: Iterable[TransformDependency]): AnnotationSeq = {
    val emitters = transforms.map(_.getObject()).collect { case e: firrtl2.Emitter => e }
    emitters.map(e => EmitCircuitAnnotation(e.getClass)).toSeq
  }
}

/** Use this if you just need to create a standard compiler and want to save some typing. */
trait MakeCompiler {
  protected def makeVerilogCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.VerilogEmitter]) ++ transforms)
  protected def makeMinimumVerilogCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.MinimumVerilogEmitter]) ++ transforms)
  protected def makeLowFirrtlCompiler(transforms: Seq[TransformDependency] = Seq()) =
    new firrtl2.stage.transforms.Compiler(Seq(Dependency[firrtl2.LowFirrtlEmitter]) ++ transforms)
}
