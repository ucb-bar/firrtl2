// SPDX-License-Identifier: Apache-2.0

package firrtl2.passes

import firrtl2.ir.Circuit
import firrtl2.{CircuitState, FirrtlUserException, Transform}

/** [[Pass]] is simple transform that is generally part of a larger [[Transform]] */
trait Pass extends Transform {
  def run(c:         Circuit): Circuit
  def execute(state: CircuitState): CircuitState = state.copy(circuit = run(state.circuit))
}

// Error handling
class PassException(message: String) extends FirrtlUserException(message)
class PassExceptions(val exceptions: Seq[PassException]) extends FirrtlUserException("\n" + exceptions.mkString("\n"))
class Errors {
  val errors = collection.mutable.ArrayBuffer[PassException]()
  def append(pe: PassException) = errors.append(pe)
  def trigger() = errors.size match {
    case 0 =>
    case 1 => throw errors.head
    case _ =>
      append(new PassException(s"${errors.length} errors detected!"))
      throw new PassExceptions(errors.toSeq)
  }
}
