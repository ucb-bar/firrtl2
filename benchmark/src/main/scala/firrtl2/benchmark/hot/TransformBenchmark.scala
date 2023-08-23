// SPDX-License-Identifier: Apache-2.0
package firrtl2
package benchmark
package hot

import firrtl2._
import firrtl2.passes.LowerTypes
import firrtl2.stage.TransformManager
import firrtl2.benchmark.util._
import firrtl2.transforms.DedupModules

abstract class TransformBenchmark(factory: () => Transform) extends App {
  val inputFile = args(0)
  val warmup = args(1).toInt
  val runs = args(2).toInt

  val input = filenameToCircuit(inputFile)
  val inputState = CircuitState(input)

  val manager = new TransformManager(factory().prerequisites)
  val preState = manager.execute(inputState)

  hot.util.benchmark(warmup, runs)(factory().transform(preState))
}

object LowerTypesBenchmark extends TransformBenchmark(() => LowerTypes)

object DedupBenchmark extends TransformBenchmark(() => new DedupModules())