// SPDX-License-Identifier: Apache-2.0

package firrtl2.passes

import firrtl2.Transform
import firrtl2.ir._
import firrtl2.options.Dependency

object CheckChirrtl extends Pass with CheckHighFormLike {

  override def prerequisites = Nil

  override val optionalPrerequisiteOf = firrtl2.stage.Forms.ChirrtlForm ++
    Seq(Dependency(CInferTypes), Dependency(CInferMDir), Dependency(RemoveCHIRRTL))

  override def invalidates(a: Transform) = false

  def errorOnChirrtl(info: Info, mname: String, s: Statement): Option[PassException] = None
}
