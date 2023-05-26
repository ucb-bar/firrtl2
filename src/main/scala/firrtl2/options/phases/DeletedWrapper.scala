// SPDX-License-Identifier: Apache-2.0

package firrtl2.options.phases

import firrtl2.AnnotationSeq
import firrtl2.annotations.DeletedAnnotation
import firrtl2.options.{Phase, Translator}

import scala.collection.mutable

/** Wrap a [[firrtl2.options.Phase Phase]] such that any [[firrtl2.annotations.Annotation Annotation]] removed by the
  * wrapped [[firrtl2.options.Phase Phase]] will be added as [[firrtl2.annotations.DeletedAnnotation DeletedAnnotation]]s.
  *
  * @param p a [[firrtl2.options.Phase Phase]] to wrap
  */
class DeletedWrapper(p: Phase) extends Phase with Translator[AnnotationSeq, (AnnotationSeq, AnnotationSeq)] {

  override def prerequisites = Seq.empty

  override def optionalPrerequisiteOf = Seq.empty

  override def invalidates(a: Phase) = false

  override lazy val name: String = p.name

  def aToB(a: AnnotationSeq): (AnnotationSeq, AnnotationSeq) = (a, a)

  def bToA(b: (AnnotationSeq, AnnotationSeq)): AnnotationSeq = {

    val (in, out) = (mutable.LinkedHashSet() ++ b._1, mutable.LinkedHashSet() ++ b._2)

    (in -- out).map {
      case DeletedAnnotation(n, a) => DeletedAnnotation(s"$n+$name", a)
      case a                       => DeletedAnnotation(name, a)
    }.toSeq ++ b._2

  }

  def internalTransform(b: (AnnotationSeq, AnnotationSeq)): (AnnotationSeq, AnnotationSeq) = (b._1, p.transform(b._2))

}

object DeletedWrapper {

  /** Wrap a [[firrtl2.options.Phase Phase]] in a [[DeletedWrapper]]
    *
    * @param p a [[firrtl2.options.Phase Phase]] to wrap
    */
  def apply(p: Phase): DeletedWrapper = new DeletedWrapper(p)

}
