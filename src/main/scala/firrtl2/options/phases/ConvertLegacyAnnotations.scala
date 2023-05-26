// SPDX-License-Identifier: Apache-2.0

package firrtl2.options.phases

import firrtl2.AnnotationSeq
import firrtl2.options.{Dependency, Phase}

@deprecated("LegacyAnnotation has been removed, this is a no-op", "FIRRTL 1.4")
class ConvertLegacyAnnotations extends Phase {

  override def prerequisites = Seq(Dependency[GetIncludes])

  override def optionalPrerequisiteOf = Seq.empty

  override def invalidates(a: Phase) = false

  def transform(annotations: AnnotationSeq): AnnotationSeq = annotations
}
