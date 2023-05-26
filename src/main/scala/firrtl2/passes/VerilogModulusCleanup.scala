// SPDX-License-Identifier: Apache-2.0

package firrtl2
package passes

import firrtl2.ir._
import firrtl2.options.Dependency

import scala.collection.mutable

/**
  * Verilog has the width of (a % b) = Max(W(a), W(b))
  * FIRRTL has the width of (a % b) = Min(W(a), W(b)), which makes more sense,
  * but nevertheless is a problem when emitting verilog
  *
  * This pass finds every instance of (a % b) and:
  *   1) adds a temporary node equal to (a % b) with width Max(W(a), W(b))
  *   2) replaces the reference to (a % b) with a bitslice of the temporary node
  *      to get back down to width Min(W(a), W(b))
  *
  *  This is technically incorrect firrtl, but allows the verilog emitter
  *  to emit correct verilog without needing to add temporary nodes
  */
@deprecated("This pass's functionality has been moved to LegalizeVerilog", "FIRRTL 1.5.2")
object VerilogModulusCleanup extends Pass {

  override def prerequisites = firrtl2.stage.Forms.LowFormMinimumOptimized ++
    Seq(
      Dependency[firrtl2.transforms.BlackBoxSourceHelper],
      Dependency[firrtl2.transforms.FixAddingNegativeLiterals],
      Dependency[firrtl2.transforms.ReplaceTruncatingArithmetic],
      Dependency[firrtl2.transforms.InlineBitExtractionsTransform],
      Dependency[firrtl2.transforms.InlineAcrossCastsTransform],
      Dependency[firrtl2.transforms.LegalizeClocksAndAsyncResetsTransform],
      Dependency[firrtl2.transforms.FlattenRegUpdate]
    )

  override def optionalPrerequisites = firrtl2.stage.Forms.LowFormOptimized

  override def optionalPrerequisiteOf = Seq.empty

  override def invalidates(a: Transform) = false

  def run(c: Circuit): Circuit = c
}
