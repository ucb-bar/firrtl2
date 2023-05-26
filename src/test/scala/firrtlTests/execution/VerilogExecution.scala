// SPDX-License-Identifier: Apache-2.0

package firrtlTests.execution

import java.io.File
import firrtl2._
import firrtl2.ir._
import firrtl2.stage.{FirrtlCircuitAnnotation, FirrtlStage}
import firrtl2.options.TargetDirAnnotation
import firrtl2.util.BackendCompilationUtilities._

/**
  * Mixing in this trait causes a SimpleExecutionTest to be run in Verilog simulation.
  */
trait VerilogExecution extends TestExecution {
  this: SimpleExecutionTest =>

  /** can be overwritten to mix-in custom annotations */
  val customAnnotations: AnnotationSeq = Seq()

  def runEmittedDUT(c: Circuit, testDir: File): Unit = {
    // Run FIRRTL, emit Verilog file
    val cAnno = FirrtlCircuitAnnotation(c)
    val tdAnno = TargetDirAnnotation(testDir.getAbsolutePath)

    (new FirrtlStage).execute(Array.empty, AnnotationSeq(Seq(cAnno, tdAnno)) ++ customAnnotations)

    // Copy harness resource to test directory
    val harness = new File(testDir, s"top.cpp")
    copyResourceToFile(cppHarnessResourceName, harness)

    // Make and run Verilog simulation
    verilogToCpp(c.main, testDir, Nil, harness) #&&
      cppToExe(c.main, testDir) ! loggingProcessLogger
    assert(executeExpectingSuccess(c.main, testDir))
  }
}
