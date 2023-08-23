// SPDX-License-Identifier: Apache-2.0

package firrtlTests

import firrtl2._
import firrtl2.passes._
import firrtl2.testutils.FirrtlFlatSpec

class ZeroLengthVecsSpec extends FirrtlFlatSpec {
  val transforms = Seq(ResolveKinds, InferTypes, ResolveFlows, new InferWidths, ZeroLengthVecs, CheckTypes)
  protected def exec(input: String) = {
    transforms
      .foldLeft(CircuitState(parse(input))) { (c: CircuitState, t: Transform) =>
        t.runTransform(c)
      }
      .circuit
      .serialize
  }

  "ZeroLengthVecs" should "drop subaccesses to zero-length vectors" in {
    val input =
      """circuit bar :
        |  module bar :
        |    input i : { a : UInt<8>, b : UInt<4> }[0]
        |    input sel : UInt<1>
        |    output foo : UInt<1>[0]
        |    output o : UInt<8>
        |    foo[UInt<1>(0)] <= UInt<1>(0)
        |    o <= i[sel].a
        |""".stripMargin
    val check =
      """circuit bar :
        |  module bar :
        |    input i : { a : UInt<8>, b : UInt<4> }[0]
        |    input sel : UInt<1>
        |    output foo : UInt<1>[0]
        |    output o : UInt<8>
        |    skip
        |    o <= validif(UInt<1>(0), UInt<8>(0))
        |""".stripMargin
    (parse(exec(input))) should be(parse(check))
  }
}
