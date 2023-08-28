// SPDX-License-Identifier: Apache-2.0
package firrtl2
package benchmark
package hot

import firrtl2.passes._
import firrtl2.stage.TransformManager

import firrtl2.benchmark.util._

abstract class PassBenchmark(passFactory: () => Pass) extends App {
  val inputFile = args(0)
  val warmup = args(1).toInt
  val runs = args(2).toInt

  val input = filenameToCircuit(inputFile)
  val inputState = CircuitState(input)

  val manager = new TransformManager(passFactory().prerequisites)
  val preState = manager.execute(inputState)

  hot.util.benchmark(warmup, runs)(passFactory().run(preState.circuit))
}

object ResolveKindsBenchmark extends PassBenchmark(() => ResolveKinds)

object CheckHighFormBenchmark extends PassBenchmark(() => CheckHighForm)

object CheckWidthsBenchmark extends PassBenchmark(() => CheckWidths)
