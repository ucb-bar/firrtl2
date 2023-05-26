// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage.transforms

import firrtl2.options.DependencyManagerUtils.CharSet
import firrtl2.stage.TransformManager
import firrtl2.{Transform, VerilogEmitter}

/** A [[firrtl2.stage.TransformManager TransformManager]] of
  */
class Compiler(
  targets:      Seq[TransformManager.TransformDependency],
  currentState: Seq[TransformManager.TransformDependency] = Seq.empty,
  knownObjects: Set[Transform] = Set.empty)
    extends TransformManager(targets, currentState, knownObjects) {

  override val wrappers = Seq(
    (a: Transform) => ExpandPrepares(a),
    (a: Transform) => CatchCustomTransformExceptions(a),
    (a: Transform) => UpdateAnnotations(a)
  )

  override def customPrintHandling(
    tab:     String,
    charSet: CharSet,
    size:    Int
  ): Option[PartialFunction[(Transform, Int), Seq[String]]] = {

    val (l, n, c) = (charSet.lastNode, charSet.notLastNode, charSet.continuation)
    val last = size - 1

    val f: PartialFunction[(Transform, Int), Seq[String]] = {
      {
        case (a: VerilogEmitter, `last`) =>
          val firstTransforms = a.transforms.dropRight(1)
          val lastTransform = a.transforms.last
          Seq(s"$tab$l ${a.name}") ++
            firstTransforms.map(t => s"""$tab${" " * c.size} $n ${t.name}""") :+
            s"""$tab${" " * c.size} $l ${lastTransform.name}"""
        case (a: VerilogEmitter, _) =>
          val firstTransforms = a.transforms.dropRight(1)
          val lastTransform = a.transforms.last
          Seq(s"$tab$n ${a.name}") ++
            firstTransforms.map(t => s"""$tab$c $n ${t.name}""") :+
            s"""$tab$c $l ${lastTransform.name}"""
      }
    }

    Some(f)
  }

}
