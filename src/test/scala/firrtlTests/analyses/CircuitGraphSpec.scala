// SPDX-License-Identifier: Apache-2.0

package firrtlTests.analyses

import firrtl2.analyses.CircuitGraph
import firrtl2.annotations.CircuitTarget
import firrtl2.options.Dependency
import firrtl2.passes.ExpandWhensAndCheck
import firrtl2.testutils.FirrtlFlatSpec
import firrtl2.CircuitState

class CircuitGraphSpec extends FirrtlFlatSpec {
  "CircuitGraph" should "find paths with deep hierarchy quickly" in {
    def mkChild(n: Int): String =
      s"""  module Child${n} :
         |    input in: UInt<8>
         |    output out: UInt<8>
         |    inst c1 of Child${n + 1}
         |    inst c2 of Child${n + 1}
         |    c1.in <= in
         |    c2.in <= c1.out
         |    out <= c2.out
         """.stripMargin
    def mkLeaf(n: Int): String =
      s"""  module Child${n} :
         |    input in: UInt<8>
         |    output out: UInt<8>
         |    wire middle: UInt<8>
         |    middle <= in
         |    out <= middle
         """.stripMargin
    (2 until 23 by 2).foreach { n =>
      val input = new StringBuilder()
      input ++=
        """circuit Child0:
          |""".stripMargin
      (0 until n).foreach { i => input ++= mkChild(i); input ++= "\n" }
      input ++= mkLeaf(n)
      val circuit = new firrtl2.stage.transforms.Compiler(Seq(Dependency[ExpandWhensAndCheck]))
        .runTransform(
          CircuitState(parse(input.toString()))
        )
        .circuit
      val circuitGraph = CircuitGraph(circuit)
      val C = CircuitTarget("Child0")
      val Child0 = C.module("Child0")
      circuitGraph.connectionPath(Child0.ref("in"), Child0.ref("out"))
    }
  }

}
