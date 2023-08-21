// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage.phases

import firrtl2.{AnnotationSeq, EmitAnnotation, EmitCircuitAnnotation, Emitter}
import firrtl2.stage.RunFirrtlTransformAnnotation
import firrtl2.options.{Dependency, Phase}

/** [[firrtl2.options.Phase Phase]] that adds a [[firrtl2.EmitCircuitAnnotation EmitCircuitAnnotation]] */
class AddImplicitEmitter extends Phase {

  override def prerequisites = Seq(Dependency[AddDefaults])

  override def optionalPrerequisiteOf = Seq.empty

  override def invalidates(a: Phase) = false

  def transform(annos: AnnotationSeq): AnnotationSeq = {
    val emit = annos.collectFirst { case a: EmitAnnotation => a }
    val emitter = annos.collectFirst { case RunFirrtlTransformAnnotation(e: Emitter) => e }

    if (emit.isEmpty && emitter.nonEmpty) {
      annos.flatMap {
        case a @ RunFirrtlTransformAnnotation(e: Emitter) => Seq(a, EmitCircuitAnnotation(e.getClass))
        case a => Some(a)
      }
    } else {
      annos
    }
  }

}
