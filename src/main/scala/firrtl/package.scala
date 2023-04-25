// SPDX-License-Identifier: Apache-2.0

import firrtl.annotations.Annotation

package object firrtl {
  // Force initialization of the Forms object - https://github.com/freechipsproject/firrtl/issues/1462
  private val _dummyForms = firrtl.stage.Forms

  implicit def seqToAnnoSeq(xs: Seq[Annotation]): AnnotationSeq = AnnotationSeq(xs)
  implicit def annoSeqToSeq(as: AnnotationSeq): Seq[Annotation] = as.toSeq

  /* Options as annotations compatibility items */
  @deprecated("Use firrtl.options.TargetDirAnnotation", "FIRRTL 1.2")
  type TargetDirAnnotation = firrtl.options.TargetDirAnnotation

  @deprecated("Use firrtl.options.TargetDirAnnotation", "FIRRTL 1.2")
  val TargetDirAnnotation = firrtl.options.TargetDirAnnotation

  type WRef = ir.Reference
  type WSubField = ir.SubField
  type WSubIndex = ir.SubIndex
  type WSubAccess = ir.SubAccess
  type WDefInstance = ir.DefInstance

  /** Container of all annotations for a Firrtl compiler */
  // AnnotationSeq should be opaque to match Scala 2.x behavior, but it is a **useless** abstraction
  //   The API is the same, but the underlying Seq[Annotation] is no longer private
  //   There are many cases where the Scala 3 compiler can't infer AnnotationSeq =:= Seq[Annotation] and that results in many type errors
  //   The way to resolve it, is explicit applications of apply() or toSeq(), which breaks the opacity of AnnotationSeq anyways
  type AnnotationSeq = Seq[Annotation]

  object AnnotationSeq {
    def apply(xs: Seq[Annotation]): AnnotationSeq = xs
  }
}
