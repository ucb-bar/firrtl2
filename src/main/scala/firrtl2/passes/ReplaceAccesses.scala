// SPDX-License-Identifier: Apache-2.0

package firrtl2.passes

import firrtl2.Transform
import firrtl2.ir._
import firrtl2.{WSubAccess, WSubIndex}
import firrtl2.Mappers._
import firrtl2.options.Dependency

/** Replaces constant [[firrtl2.WSubAccess]] with [[firrtl2.WSubIndex]]
  * TODO Fold in to High Firrtl Const Prop
  */
object ReplaceAccesses extends Pass {

  override def prerequisites = firrtl2.stage.Forms.Deduped :+ Dependency(PullMuxes)

  override def invalidates(a: Transform) = false

  def run(c: Circuit): Circuit = {
    def onStmt(s: Statement): Statement = s.map(onStmt).map(onExp)
    def onExp(e:  Expression): Expression = e match {
      case WSubAccess(ex, UIntLiteral(value, _), t, g) =>
        ex.tpe match {
          case VectorType(_, len) if (value < len) => WSubIndex(onExp(ex), value.toInt, t, g)
          case _                                   => e.map(onExp)
        }
      case _ => e.map(onExp)
    }

    c.copy(modules = c.modules.map(_.map(onStmt)))
  }
}
