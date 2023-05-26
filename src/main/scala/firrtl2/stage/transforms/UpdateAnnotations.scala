// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage.transforms

import firrtl2.{CircuitState, Transform}
import firrtl2.options.Translator

class UpdateAnnotations(val underlying: Transform)
    extends Transform
    with WrappedTransform
    with Translator[CircuitState, (CircuitState, CircuitState)] {

  override def execute(c: CircuitState): CircuitState = underlying.transform(c)

  def aToB(a: CircuitState): (CircuitState, CircuitState) = (a, a)

  def bToA(b: (CircuitState, CircuitState)): CircuitState = {
    Transform.remapAnnotations(b._2, logger)
  }

  def internalTransform(b: (CircuitState, CircuitState)): (CircuitState, CircuitState) = {
    val result = underlying.transform(b._2)
    (b._1, result)
  }
}

object UpdateAnnotations {

  def apply(a: Transform): UpdateAnnotations = new UpdateAnnotations(a)

}
