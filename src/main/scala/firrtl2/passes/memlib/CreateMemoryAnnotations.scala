// SPDX-License-Identifier: Apache-2.0

package firrtl2
package passes
package memlib

import firrtl2.stage.Forms

class CreateMemoryAnnotations extends Transform {

  override def prerequisites = Forms.MidForm
  override def optionalPrerequisites = Seq.empty
  override def optionalPrerequisiteOf = Forms.MidEmitters
  override def invalidates(a: Transform) = false

  def execute(state: CircuitState): CircuitState = {
    state.copy(annotations = state.annotations.flatMap {
      case ReplSeqMemAnnotation(outputConfig) =>
        Seq(MemLibOutConfigFileAnnotation(outputConfig, Nil))
      case a => Seq(a)
    })
  }
}
