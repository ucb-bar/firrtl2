// SPDX-License-Identifier: Apache-2.0

package firrtl2.passes
package clocklist

import firrtl2._
import annotations._
import Utils.error
import java.io.{PrintWriter, Writer}
import Utils._
import memlib._
import firrtl2.options.{RegisteredTransform, ShellOption}
import firrtl2.stage.{Forms, RunFirrtlTransformAnnotation}

case class ClockListAnnotation(target: ModuleName, outputConfig: String) extends SingleTargetAnnotation[ModuleName] {
  def duplicate(n: ModuleName) = ClockListAnnotation(n, outputConfig)
}

object ClockListAnnotation {
  def parse(t: String): ClockListAnnotation = {
    val usage = """
[Optional] ClockList
  List which signal drives each clock of every descendent of specified module

Usage:
  --list-clocks -c:<circuit>:-m:<module>:-o:<filename>
  *** Note: sub-arguments to --list-clocks should be delimited by : and not white space!
"""

    //Parse pass options
    val passOptions = PassConfigUtil.getPassOptions(t, usage)
    val outputConfig = passOptions.getOrElse(
      OutputConfigFileName,
      error("No output config file provided for ClockList!" + usage)
    )
    val passCircuit = passOptions.getOrElse(
      PassCircuitName,
      error("No circuit name specified for ClockList!" + usage)
    )
    val passModule = passOptions.getOrElse(
      PassModuleName,
      error("No module name specified for ClockList!" + usage)
    )
    val target = ModuleName(passModule, CircuitName(passCircuit))
    ClockListAnnotation(target, outputConfig)
  }
}

class ClockListTransform extends Transform with RegisteredTransform {

  override def prerequisites = Forms.LowForm
  override def optionalPrerequisites = Seq.empty
  override def optionalPrerequisiteOf = Forms.LowEmitters

  val options = Seq(
    new ShellOption[String](
      longOption = "list-clocks",
      toAnnotationSeq = (a: String) =>
        Seq(passes.clocklist.ClockListAnnotation.parse(a), RunFirrtlTransformAnnotation(new ClockListTransform)),
      helpText = "List which signal drives each clock of every descendent of specified modules",
      shortOption = Some("clks"),
      helpValueName = Some("-c:<circuit>:-m:<module>:-o:<filename>")
    )
  )

  def passSeq(top: String, writer: Writer): Seq[Pass] =
    Seq(new ClockList(top, writer))
  def execute(state: CircuitState): CircuitState = {
    val annos = state.annotations.collect { case a: ClockListAnnotation => a }
    annos match {
      case Seq(ClockListAnnotation(ModuleName(top, CircuitName(state.circuit.main)), out)) =>
        val outputFile = new PrintWriter(out)
        val newC = (new ClockList(top, outputFile)).run(state.circuit)
        outputFile.close()
        CircuitState(newC, state.annotations)
      case Nil => state
      case seq => error(s"Found illegal clock list annotation(s): $seq")
    }
  }
}
