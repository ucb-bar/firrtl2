// SPDX-License-Identifier: Apache-2.0

package firrtl2.annotations.transforms

import firrtl2._
import firrtl2.annotations.{CircuitTarget, ModuleTarget, MultiTargetAnnotation, ReferenceTarget, SingleTargetAnnotation}
import firrtl2.ir
import firrtl2.options.Dependency
import firrtl2.traversals.Foreachers._
import firrtl2.renamemap.MutableRenameMap

import scala.collection.immutable.{Set => ISet}

/** Replaces all [[firrtl2.annotations.ReferenceTarget ReferenceTargets]] pointing at instances with
  * [[firrtl2.annotations.InstanceTarget InstanceTargets]].
  *
  * @note This exists because of [[firrtl2.annotations.Named Named]] where a [[firrtl2.annotations.ComponentName
  * ComponentName]] is the only way to refer to an instance, but this is resolved incorrectly to a
  *       [[firrtl2.annotations.ReferenceTarget ReferenceTarget]].
  */
class CleanupNamedTargets extends Transform {

  override def prerequisites = Seq(Dependency(passes.RemoveCHIRRTL))

  override def optionalPrerequisites = Seq.empty

  override def optionalPrerequisiteOf = Seq.empty

  override def invalidates(a: Transform) = false

  private def onStatement(
    statement: ir.Statement
  )(
    implicit references: ISet[ReferenceTarget],
    renameMap:           MutableRenameMap,
    module:              ModuleTarget
  ): Unit = statement match {
    case ir.DefInstance(_, a, b, _) if references(module.instOf(a, b).asReference) =>
      renameMap.record(module.instOf(a, b).asReference, module.instOf(a, b))
    case a => statement.foreach(onStatement)
  }

  private def onModule(
    module: ir.DefModule
  )(
    implicit references: ISet[ReferenceTarget],
    renameMap:           MutableRenameMap,
    circuit:             CircuitTarget
  ): Unit = {
    implicit val mTarget = circuit.module(module.name)
    module.foreach(onStatement)
  }

  override protected def execute(state: CircuitState): CircuitState = {

    implicit val rTargets: ISet[ReferenceTarget] = state.annotations.flatMap {
      case a: SingleTargetAnnotation[_] => Some(a.target)
      case a: MultiTargetAnnotation     => a.targets.flatten
      case _ => None
    }.collect {
      case a: ReferenceTarget => a
    }.toSet

    implicit val renameMap = MutableRenameMap()

    implicit val cTarget = CircuitTarget(state.circuit.main)

    state.circuit.foreach(onModule)

    state.copy(renames = Some(renameMap))
  }

}
