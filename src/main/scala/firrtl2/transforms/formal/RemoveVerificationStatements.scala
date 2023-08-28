// SPDX-License-Identifier: Apache-2.0

package firrtl2.transforms.formal

import firrtl2.ir.{Circuit, EmptyStmt, Statement, Verification}
import firrtl2.{CircuitState, MinimumVerilogEmitter, Transform, VerilogEmitter}
import firrtl2.options.{Dependency, StageUtils}
import firrtl2.stage.TransformManager.TransformDependency

/**
  * Remove Verification Statements
  *
  * Replaces all verification statements in all modules with the empty statement.
  * This is intended to be required by the Verilog emitter to ensure compatibility
  * with the Verilog 2001 standard.
  */
class RemoveVerificationStatements extends Transform {

  override def invalidates(a: Transform) = false
  override def prerequisites:         Seq[TransformDependency] = Seq.empty
  override def optionalPrerequisites: Seq[TransformDependency] = Seq(Dependency(ConvertAsserts))
  override def optionalPrerequisiteOf: Seq[TransformDependency] =
    Seq(Dependency[VerilogEmitter], Dependency[MinimumVerilogEmitter])

  private var removedCounter = 0

  def removeVerification(s: Statement): Statement = s match {
    case _: Verification => {
      removedCounter += 1
      EmptyStmt
    }
    case t => t.mapStmt(removeVerification)
  }

  def run(c: Circuit): Circuit = {
    c.mapModule(mod => {
      mod.mapStmt(removeVerification)
    })
  }

  def execute(state: CircuitState): CircuitState = {
    val newState = state.copy(circuit = run(state.circuit))
    if (removedCounter > 0) {
      StageUtils.dramaticWarning(
        s"$removedCounter verification statements " +
          "(assert, assume or cover) " +
          "were removed when compiling to Verilog because the basic Verilog " +
          "standard does not support them. If this was not intended, compile " +
          "to System Verilog instead using the `-X sverilog` compiler flag."
      )
    }
    newState
  }
}
