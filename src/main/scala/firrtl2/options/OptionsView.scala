// SPDX-License-Identifier: Apache-2.0

package firrtl2.options

import firrtl2.AnnotationSeq

/** Type class defining a "view" of an [[firrtl2.AnnotationSeq AnnotationSeq]]
  *
  * @tparam T the type to which this viewer converts an [[firrtl2.AnnotationSeq AnnotationSeq]] to
  */
trait OptionsView[T] {

  /** Convert an [[firrtl2.AnnotationSeq AnnotationSeq]] to some other type
    *
    * @param options some annotations
    */
  def view(options: AnnotationSeq): T

}

/** A shim to manage multiple "views" of an [[firrtl2.AnnotationSeq AnnotationSeq]] */
object Viewer {

  /** Helper method to get at a given [[OptionsView]]. This enables access to [[OptionsView]] methods in a more canonical
    * format, e.g., you can then do `Viewer[T].view`.
    * @param a an implicit [[OptionsView]]
    */
  def apply[T](implicit a: OptionsView[T]): OptionsView[T] = a

  /** Convert annotations to options using an implicitly provided [[OptionsView]]
    *
    * @param options some annotations
    * @tparam T the type to which the input [[firrtl2.AnnotationSeq AnnotationSeq]] should be viewed as
    */
  def view[T: OptionsView](options: AnnotationSeq): T = Viewer[T].view(options)

}
