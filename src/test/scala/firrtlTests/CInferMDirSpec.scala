// SPDX-License-Identifier: Apache-2.0

package firrtlTests

import firrtl2._
import firrtl2.ir._
import firrtl2.options.Dependency
import firrtl2.passes._
import firrtl2.stage.Forms
import firrtl2.transforms._
import firrtl2.testutils._

object CInferMDirCheckPass extends Pass {
  override def prerequisites = Forms.LowForm

  // finds the memory and check its read port
  def checkStmt(s: Statement): Boolean = s match {
    case s: DefMemory if s.name == "indices" =>
      (s.readers contains "index") &&
        (s.writers contains "bar") &&
        s.readwriters.isEmpty
    case s: Block =>
      s.stmts.exists(checkStmt)
    case _ => false
  }

  def run(c: Circuit) = {
    val errors = new Errors
    val check = c.modules.exists {
      case m: Module    => checkStmt(m.body)
      case m: ExtModule => false
    }
    if (!check) {
      errors.append(new PassException("Memory has incorrect port directions!"))
      errors.trigger()
    }
    c
  }
}

class CInferMDirSpec extends LowFirrtlTransformSpec(Seq(Dependency(CInferMDirCheckPass))) {

  "Memory" should "have correct mem port directions" in {
    val input = """
circuit foo :
  module foo :
    input clock : Clock
    input reset : UInt<1>
    output io : {flip wen : UInt<1>, flip in : UInt<1>, flip counter : UInt<2>, ren: UInt<1>[4], out : UInt<1>[4]}

    io is invalid
    cmem indices : UInt<2>[4]
    node T_0 = add(io.counter, UInt<1>("h01"))
    node temp = tail(T_0, 1)
    infer mport index = indices[temp], clock
    io.out[0] <= UInt<1>("h0")
    io.out[1] <= UInt<1>("h0")
    io.out[2] <= UInt<1>("h0")
    io.out[3] <= UInt<1>("h0")
    when io.ren[index] :
      io.out[index] <= io.in
    else :
      when io.wen :
        infer mport bar = indices[temp], clock
        bar <= io.in
""".stripMargin

    val res = compile(input)
    // Check correctness of firrtl
    parse(res.getEmittedCircuit.value)
  }
}
