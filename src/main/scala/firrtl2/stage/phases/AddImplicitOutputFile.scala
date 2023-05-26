// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage.phases

import firrtl2.{AnnotationSeq, EmitAllModulesAnnotation}
import firrtl2.options.{Dependency, Phase, Viewer}
import firrtl2.stage.{FirrtlOptions, OutputFileAnnotation}
import firrtl2.stage.FirrtlOptionsView

/** [[firrtl2.options.Phase Phase]] that adds an [[OutputFileAnnotation]] if one does not already exist.
  *
  * To determine the [[OutputFileAnnotation]], the following precedence is used. Whichever happens first succeeds:
  *  - Do nothing if an [[OutputFileAnnotation]] or [[EmitAllModulesAnnotation]] exist
  *  - Use the main in the first discovered [[firrtl2.stage.FirrtlCircuitAnnotation FirrtlCircuitAnnotation]] (see note
  *    below)
  *  - Use "a"
  *
  * The file suffix may or may not be specified, but this may be arbitrarily changed by the [[Emitter]].
  *
  * @note This [[firrtl2.options.Phase Phase]] has a dependency on [[AddCircuit]]. Only a
  *       [[firrtl2.stage.FirrtlCircuitAnnotation FirrtlCircuitAnnotation]] will be used to implicitly set the
  *       [[OutputFileAnnotation]] (not other [[firrtl2.stage.CircuitOption CircuitOption]] subclasses).
  */
class AddImplicitOutputFile extends Phase {

  override def prerequisites = Seq(Dependency[AddCircuit])

  override def optionalPrerequisiteOf = Seq.empty

  override def invalidates(a: Phase) = false

  /** Add an [[OutputFileAnnotation]] to an [[AnnotationSeq]] */
  def transform(annotations: AnnotationSeq): AnnotationSeq =
    annotations.collectFirst { case _: OutputFileAnnotation | _: EmitAllModulesAnnotation => annotations }.getOrElse {
      val topName = Viewer[FirrtlOptions]
        .view(annotations)
        .firrtlCircuit
        .map(_.main)
        .getOrElse("a")
      OutputFileAnnotation(topName) +: annotations
    }
}
