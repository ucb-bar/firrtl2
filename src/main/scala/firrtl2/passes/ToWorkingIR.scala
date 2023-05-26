// SPDX-License-Identifier: Apache-2.0

package firrtl2.passes

import firrtl2.ir._
import firrtl2.Transform

@deprecated(
  "This pass is an identity transform. For an equivalent dependency, use firrtl.stage.forms.MinimalHighForm",
  "FIRRTL 1.4.2"
)
object ToWorkingIR extends Pass {
  override def prerequisites = firrtl2.stage.Forms.MinimalHighForm
  override def optionalPrerequisites = Seq.empty
  override def optionalPrerequisiteOf =
    (firrtl2.stage.Forms.LowFormOptimized.toSet -- firrtl2.stage.Forms.MinimalHighForm).toSeq
  override def invalidates(a: Transform) = false
  def run(c:                  Circuit): Circuit = c
}
