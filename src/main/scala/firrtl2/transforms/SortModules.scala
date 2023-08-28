// SPDX-License-Identifier: Apache-2.0

package firrtl2.transforms

import firrtl2.{CircuitState, Transform}
import firrtl2.analyses.InstanceKeyGraph
import firrtl2.options.Dependency
import firrtl2.stage.Forms

/** Return a circuit where all modules (and external modules) are defined before use. */
class SortModules extends Transform {

  override def prerequisites = Seq(Dependency(firrtl2.passes.CheckChirrtl))
  override def optionalPrerequisites = Seq.empty
  override def optionalPrerequisiteOf = Forms.ChirrtlEmitters
  override def invalidates(a: Transform) = false

  override def execute(state: CircuitState): CircuitState = {
    val modulesx = InstanceKeyGraph(state.circuit).moduleOrder.reverse
    state.copy(circuit = state.circuit.copy(modules = modulesx))
  }

}
