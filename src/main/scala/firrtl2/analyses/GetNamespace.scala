// SPDX-License-Identifier: Apache-2.0

package firrtl2.analyses

import firrtl2.annotations.NoTargetAnnotation
import firrtl2.{CircuitState, Namespace, Transform}
import firrtl2.stage.Forms

case class ModuleNamespaceAnnotation(namespace: Namespace) extends NoTargetAnnotation

/** Create a namespace with this circuit
  *
  * namespace is used by RenameModules to get unique names
  */
class GetNamespace extends Transform {
  override def prerequisites = Forms.LowForm
  override def optionalPrerequisites = Seq.empty
  override def optionalPrerequisiteOf = Forms.LowEmitters
  override def invalidates(a: Transform) = false

  def execute(state: CircuitState): CircuitState = {
    val namespace = Namespace(state.circuit)
    state.copy(annotations = new ModuleNamespaceAnnotation(namespace) +: state.annotations)
  }
}
