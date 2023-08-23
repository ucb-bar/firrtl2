// SPDX-License-Identifier: Apache-2.0

package firrtlTests

import firrtl2._
import firrtl2.testutils._
import FirrtlCheckers._

class MemSpec extends VerilogTransformSpec {

  "Zero-ported mems" should "be supported!" in {
    FirrtlRunners.runFirrtlTest("ZeroPortMem", "/features")
  }

  "Mems with zero-width elements" should "be supported!" in {
    FirrtlRunners.runFirrtlTest("ZeroWidthMem", "/features")
  }

  "Very large memories" should "be supported" in {
    val addrWidth = 65
    val memSize = BigInt(1) << addrWidth
    val input =
      s"""
         |circuit Test :
         |  module Test :
         |    input clock : Clock
         |    input raddr : UInt<$addrWidth>
         |    output rdata : UInt<8>
         |    input wdata : UInt<8>
         |    input waddr : UInt<$addrWidth>
         |    input wen : UInt<1>
         |
         |    mem m :
         |      data-type => UInt<8>
         |      depth => $memSize
         |      reader => r
         |      writer => w
         |      read-latency => 1
         |      write-latency => 1
         |      read-under-write => undefined
         |    rdata <= m.r.data
         |    m.r.addr <= raddr
         |    m.r.en <= UInt(1)
         |    m.r.clk <= clock
         |    m.w.addr <= waddr
         |    m.w.data <= wdata
         |    m.w.en <= wen
         |    m.w.clk <= clock
         |    m.w.mask <= UInt(1)
       """.stripMargin
    val result = compile(input)
    // TODO Not great that it includes the sparse comment for VCS
    result should containLine(s"reg /* sparse */ [7:0] m [0:$addrWidth'd${memSize - 1}];")
  }

  "Very large CHIRRTL memories" should "be supported" in {
    val addrWidth = 65
    val memSize = BigInt(1) << addrWidth
    val input =
      s"""
         |circuit Test :
         |  module Test :
         |    input clock : Clock
         |    input raddr : UInt<$addrWidth>
         |    output rdata : UInt<8>
         |    input wdata : UInt<8>
         |    input waddr : UInt<$addrWidth>
         |    input wen : UInt<1>
         |
         |    cmem m : UInt<8>[$memSize]
         |    read mport r = m[raddr], clock
         |    rdata <= r
         |    write mport w = m[waddr], clock
         |    when wen :
         |      w <= wdata
       """.stripMargin
    val result = compile(input)
    // TODO Not great that it includes the sparse comment for VCS
    result should containLine(s"reg /* sparse */ [7:0] m [0:$addrWidth'd${memSize - 1}];")
  }
}
