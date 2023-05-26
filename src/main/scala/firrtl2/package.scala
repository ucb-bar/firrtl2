// SPDX-License-Identifier: Apache-2.0

import firrtl2.annotations.Annotation

package object firrtl2 {
  // Force initialization of the Forms object - https://github.com/freechipsproject/firrtl/issues/1462
  private val _dummyForms = firrtl2.stage.Forms

  implicit def seqToAnnoSeq(xs: Seq[Annotation]): AnnotationSeq = AnnotationSeq(xs)
  implicit def annoSeqToSeq(as: AnnotationSeq):   Seq[Annotation] = as.toSeq

  /* Options as annotations compatibility items */
  @deprecated("Use firrtl.options.TargetDirAnnotation", "FIRRTL 1.2")
  type TargetDirAnnotation = firrtl2.options.TargetDirAnnotation

  @deprecated("Use firrtl.options.TargetDirAnnotation", "FIRRTL 1.2")
  val TargetDirAnnotation = firrtl2.options.TargetDirAnnotation

  type WRef = ir.Reference
  type WSubField = ir.SubField
  type WSubIndex = ir.SubIndex
  type WSubAccess = ir.SubAccess
  type WDefInstance = ir.DefInstance

  /** Container of all annotations for a Firrtl compiler */
  // AnnotationSeq is now a type alias rather than a class that wraps a Seq[Annotation]
  // There is no reason for the wrapper class, since the underlying implementation won't change
  type AnnotationSeq = Seq[Annotation]
  object AnnotationSeq {
    def apply(xs: Seq[Annotation]): AnnotationSeq = xs
  }
}
