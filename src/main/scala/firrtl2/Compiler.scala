// SPDX-License-Identifier: Apache-2.0

package firrtl2

import java.io.Writer
import scala.collection.mutable
import scala.collection.immutable.VectorBuilder
import firrtl2.annotations._
import firrtl2.ir.Circuit
import firrtl2.Utils.throwInternalError
import firrtl2.annotations.transforms.{EliminateTargetPaths, ResolvePaths}
import firrtl2.logger.{LazyLogging, Logger}
import firrtl2.options.{Dependency, DependencyAPI, TransformLike}
import firrtl2.stage.Forms
import firrtl2.transforms.DedupAnnotationsTransform

/** Current State of the Circuit
  *
  * @constructor Creates a CircuitState object
  * @param circuit     The current state of the Firrtl AST
  * @param annotations The current collection of [[firrtl2.annotations.Annotation Annotation]]
  * @param renames     A map of [[firrtl2.annotations.Named Named]] things that have been renamed.
  *                    Generally only a return value from [[Transform]]s
  */
case class CircuitState(
  circuit:     Circuit,
  annotations: AnnotationSeq,
  renames:     Option[RenameMap]) {

  /** Helper for getting just an emitted circuit */
  def emittedCircuitOption: Option[EmittedCircuit] =
    emittedComponents.collectFirst { case x: EmittedCircuit => x }

  /** Helper for getting an [[EmittedCircuit]] when it is known to exist */
  def getEmittedCircuit: EmittedCircuit = emittedCircuitOption match {
    case Some(emittedCircuit) => emittedCircuit
    case None =>
      throw new FirrtlInternalException(
        s"No EmittedCircuit found! Did you delete any annotations?\n$deletedAnnotations"
      )
  }

  /** Helper function for extracting emitted components from annotations */
  def emittedComponents: Seq[EmittedComponent] =
    annotations.collect { case emitted: EmittedAnnotation[_] => emitted.value }
  def deletedAnnotations: Seq[Annotation] =
    annotations.collect { case anno: DeletedAnnotation => anno }

  /** Returns a new CircuitState with all targets being resolved.
    * Paths through instances are replaced with a uniquified final target
    * Includes modifying the circuit and annotations
    * @param targets
    * @return
    */
  def resolvePaths(targets: Seq[CompleteTarget]): CircuitState = targets match {
    case Nil => this
    case _ =>
      val newCS = new EliminateTargetPaths().runTransform(this.copy(annotations = ResolvePaths(targets) +: annotations))
      newCS.copy()
  }

  /** Returns a new CircuitState with the targets of every annotation of a type in annoClasses
    * @param annoClasses
    * @return
    */
  def resolvePathsOf(annoClasses: Class[_]*): CircuitState = {
    val targets = getAnnotationsOf(annoClasses: _*).flatMap(_.getTargets)
    if (targets.nonEmpty) resolvePaths(targets.flatMap { _.getComplete }) else this
  }

  /** Returns all annotations which are of a class in annoClasses
    * @param annoClasses
    * @return
    */
  def getAnnotationsOf(annoClasses: Class[_]*): AnnotationSeq = {
    annotations.collect { case a if annoClasses.contains(a.getClass) => a }
  }
}

object CircuitState {
  def apply(circuit: Circuit): CircuitState = apply(circuit, Seq())
  def apply(circuit: Circuit, annotations: AnnotationSeq): CircuitState =
    new CircuitState(circuit, annotations, None)
}

// Internal utilities to keep code DRY, not a clean interface
private[firrtl2] object Transform {

  def remapAnnotations(after: CircuitState, logger: Logger): CircuitState = {
    val remappedAnnotations = propagateAnnotations(after.annotations, after.renames)

    logger.trace(s"Annotations:")
    logger.trace(JsonProtocol.serializeRecover(remappedAnnotations))

    logger.trace(s"Circuit:\n${after.circuit.serialize}")

    CircuitState(after.circuit, remappedAnnotations, None)
  }

  // This function is *very* mutable but it is fairly performance critical
  def propagateAnnotations(
    resAnno:   AnnotationSeq,
    renameOpt: Option[RenameMap]
  ): AnnotationSeq = {
    // We dedup/distinct the resulting annotations when renaming occurs
    val seen = new mutable.HashSet[Annotation]
    val result = new VectorBuilder[Annotation]

    val hasRenames = renameOpt.isDefined
    val renames = renameOpt.getOrElse(null) // Null is bad but saving the allocation is worth it

    val it = resAnno.toSeq.iterator
    while (it.hasNext) {
      val anno = it.next()
      if (hasRenames) {
        val renamed = anno.update(renames)
        for (annox <- renamed) {
          if (!seen(annox)) {
            seen += annox
            result += annox
          }
        }
      } else {
        result += anno
      }
    }
    result.result()
  }
}

/** The basic unit of operating on a Firrtl AST */
trait Transform extends TransformLike[CircuitState] with DependencyAPI[Transform] {

  /** A convenience function useful for debugging and error messages */
  def name: String = this.getClass.getName

  /** Perform the transform, encode renaming with RenameMap, and can
    *   delete annotations
    * Called by [[runTransform]].
    *
    * @param state Input Firrtl AST
    * @return A transformed Firrtl AST
    */
  protected def execute(state: CircuitState): CircuitState

  def transform(state: CircuitState): CircuitState = execute(state)

  private lazy val fullCompilerSet: Set[Dependency[Transform]] = Set(Forms.VerilogOptimized: _*)

  private lazy val highOutputInvalidates = fullCompilerSet.removedAll(Forms.MinimalHighForm)
  private lazy val midOutputInvalidates = fullCompilerSet.removedAll(Forms.MidForm)

  /** Executes before any transform's execute method
    * @param state
    * @return
    */
  private[firrtl2] def prepare(state: CircuitState): CircuitState = state

  /** Perform the transform and update annotations.
    *
    * @param state Input Firrtl AST
    * @return A transformed Firrtl AST
    */
  final def runTransform(state: CircuitState): CircuitState = {
    val result = execute(prepare(state))
    Transform.remapAnnotations(result, logger)
  }

}

trait SeqTransformBased {
  def transforms: Seq[Transform]
  protected def runTransforms(state: CircuitState): CircuitState =
    transforms.foldLeft(state) { (in, xform) => xform.runTransform(in) }
}

/** For transformations that are simply a sequence of transforms */
abstract class SeqTransform extends Transform with SeqTransformBased {
  def execute(state: CircuitState): CircuitState = {
    /*
    require(state.form <= inputForm,
      s"[$name]: Input form must be lower or equal to $inputForm. Got ${state.form}")
     */
    val ret = runTransforms(state)
    CircuitState(ret.circuit, ret.annotations, ret.renames)
  }
}

/** Extend for transforms that require resolved targets in their annotations
  * Ensures all targets in annotations of a class in annotationClasses are resolved before the execute method
  */
trait ResolvedAnnotationPaths {
  this: Transform =>

  val annotationClasses: Iterable[Class[_]]

  override def prepare(state: CircuitState): CircuitState = {
    state.resolvePathsOf(annotationClasses.toSeq: _*)
  }

  // Any transform with this trait invalidates DedupAnnotationsTransform
  override def invalidates(a: Transform) = a.isInstanceOf[DedupAnnotationsTransform]
}

/** Defines old API for Emission. Deprecated */
trait Emitter extends Transform {

  override def invalidates(a: Transform) = false

  @deprecated("Use emission annotations instead", "FIRRTL 1.0")
  def emit(state: CircuitState, writer: Writer): Unit

  /** An output suffix to use if the output of this [[Emitter]] was written to a file */
  def outputSuffix: String
}
