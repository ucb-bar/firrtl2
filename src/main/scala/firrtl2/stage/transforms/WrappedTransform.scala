// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage.transforms

import firrtl2.Transform
import firrtl2.logger.Logger

/** A [[firrtl2.Transform]] that "wraps" a second [[firrtl2.Transform Transform]] to do some work before and after the
  * second [[firrtl2.Transform Transform]].
  *
  * This is intended to synergize with the [[firrtl2.options.DependencyManager.wrappers]] method.
  *
  * @see [[firrtl2.stage.transforms.CatchCustomTransformExceptions]]
  * @see [[firrtl2.stage.transforms.TrackTransforms]]
  * @see [[firrtl2.stage.transforms.UpdateAnnotations]]
  */
trait WrappedTransform { this: Transform =>

  /** The underlying [[firrtl2.Transform]] */
  val underlying: Transform

  /** Return the original [[firrtl2.Transform]] if this wrapper is wrapping other wrappers. */
  lazy final val trueUnderlying: Transform = underlying match {
    case a: WrappedTransform => a.trueUnderlying
    case _ => underlying
  }

  final override protected val logger = new Logger(trueUnderlying.getClass.getName)

  override def prerequisites = underlying.prerequisites
  @deprecated(
    "Due to confusion, 'dependents' is being renamed to 'optionalPrerequisiteOf'. Override the latter instead.",
    "FIRRTL 1.3"
  )
  override def dependents = underlying.dependents
  override def optionalPrerequisiteOf = underlying.optionalPrerequisiteOf
  override final def invalidates(b: Transform): Boolean = underlying.invalidates(b)
  override final lazy val name = underlying.name

}
