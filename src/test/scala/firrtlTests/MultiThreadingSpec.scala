// SPDX-License-Identifier: Apache-2.0

package firrtlTests

import firrtl2.FileUtils
import firrtl2.CircuitState
import firrtl2.testutils._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class MultiThreadingSpec extends FirrtlPropSpec {

  // TODO Test with annotations and source locator
  property("The FIRRTL compiler should be thread safe") {
    // Run the compiler we're testing
    def runCompiler(input: Seq[String], compiler: firrtl2.stage.transforms.Compiler): String = {
      val parsedInput = firrtl2.Parser.parse(input)
      val emitCircuitAnno = MakeCompiler.deriveEmitCircuitAnnotations(compiler).head
      val res = compiler.transform(CircuitState(parsedInput, Seq(emitCircuitAnno)))
      res.getEmittedCircuit.value
    }
    // The parameters we're testing with
    val compilers = Seq(
      MakeCompiler.makeHighFirrtlCompiler(),
      MakeCompiler.makeMiddleFirrtlCompiler(),
      MakeCompiler.makeLowFirrtlCompiler(),
      MakeCompiler.makeVerilogCompiler()
    )
    val inputFilePath = s"/integration/GCDTester.fir" // arbitrary
    val numThreads = 64 // arbitrary

    // Begin the actual test

    val inputStrings = FileUtils.getLinesResource(inputFilePath).toSeq

    import ExecutionContext.Implicits.global
    try { // Use try-catch because error can manifest in many ways
      // Execute for each compiler
      val compilerResults = compilers.map { compiler =>
        // Run compiler serially once
        val serialResult = runCompiler(inputStrings, compiler)
        Future {
          val threadFutures = (0 until numThreads).map { i =>
            Future {
              runCompiler(inputStrings, compiler) == serialResult
            }
          }
          Await.result(Future.sequence(threadFutures), Duration.Inf)
        }
      }
      val results = Await.result(Future.sequence(compilerResults), Duration.Inf)
      assert(results.flatten.reduce(_ && _)) // check all true (ie. success)
    } catch {
      case _: Throwable => fail("The Compiler is not thread safe")
    }
  }
}
