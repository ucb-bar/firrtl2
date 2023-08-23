// SPDX-License-Identifier: Apache-2.0

package firrtlTests

import firrtl2.ir.Circuit
import firrtl2._
import firrtl2.passes.Pass
import firrtl2.ir._
import firrtl2.stage.{FirrtlSourceAnnotation, FirrtlStage, RunFirrtlTransformAnnotation}
import firrtl2.options.Dependency
import firrtl2.stage.TransformManager.TransformDependency
import firrtl2.transforms.LegalizeAndReductionsTransform
import firrtl2.testutils._
import firrtl2.transforms.formal.ConvertAsserts

object CustomTransformSpec {

  class ReplaceExtModuleTransform extends SeqTransform with FirrtlMatchers {
    // Simple module
    val delayModuleString = """
                              |circuit Delay :
                              |  module Delay :
                              |    input clock : Clock
                              |    input reset : UInt<1>
                              |    input a : UInt<32>
                              |    input en : UInt<1>
                              |    output b : UInt<32>
                              |
                              |    reg r : UInt<32>, clock
                              |    r <= r
                              |    when en :
                              |      r <= a
                              |    b <= r
                              |""".stripMargin
    val delayModuleCircuit = parse(delayModuleString)
    val delayModule = delayModuleCircuit.modules.find(_.name == delayModuleCircuit.main).get

    class ReplaceExtModule extends Pass {
      def run(c: Circuit): Circuit = c.copy(
        modules = c.modules.map {
          case ExtModule(_, "Delay", _, _, _) => delayModule
          case other                          => other
        }
      )
    }
    def transforms = Seq(new ReplaceExtModule)

    override def invalidates(a: Transform) = false

    override def optionalPrerequisiteOf: Seq[TransformDependency] =
      Seq(Dependency[VerilogEmitter], Dependency[MinimumVerilogEmitter], Dependency[firrtl2.passes.ExpandWhensAndCheck])
  }

  val input = """
                |circuit test :
                |  module test :
                |    output out : UInt
                |    out <= UInt(123)""".stripMargin
  val errorString = "My Custom Transform failed!"
  class ErroringTransform extends Transform {
    def execute(state: CircuitState): CircuitState = {
      require(false, errorString)
      state
    }
  }

  object MutableState {
    var count: Int = 0
  }

  class FirstTransform extends Transform {
    override def invalidates(a: Transform) = false
    def execute(state: CircuitState): CircuitState = {
      require(MutableState.count == 0, s"Count was ${MutableState.count}, expected 0")
      MutableState.count = 1
      state
    }
  }

  class SecondTransform extends Transform {
    override def invalidates(a: Transform) = false
    override def prerequisites = Seq(Dependency[FirstTransform])
    def execute(state: CircuitState): CircuitState = {
      require(MutableState.count == 1, s"Count was ${MutableState.count}, expected 1")
      MutableState.count = 2
      state
    }
  }

  class ThirdTransform extends Transform {
    override def invalidates(a: Transform) = false
    override def prerequisites = Seq(Dependency[SecondTransform])
    def execute(state: CircuitState): CircuitState = {
      require(MutableState.count == 2, s"Count was ${MutableState.count}, expected 2")
      MutableState.count = 3
      state
    }
  }

  object Foo {
    class A extends Transform {
      def execute(s: CircuitState) = {
        assert(name.endsWith("A"))
        s
      }
    }
  }

}

class CustomTransformSpec extends FirrtlFlatSpec {

  import CustomTransformSpec._

  behavior.of("Custom Transforms")

  they should "be able to introduce high firrtl" in {
    runFirrtlTest("CustomTransform", "/features", customTransforms = List(Dependency[ReplaceExtModuleTransform]))
  }

  they should "not cause \"Internal Errors\"" in {
    (the[java.lang.IllegalArgumentException] thrownBy {
      (new FirrtlStage).execute(
        Array(),
        Seq(
          FirrtlSourceAnnotation(input),
          RunFirrtlTransformAnnotation(new ErroringTransform)
        )
      )
    }).getMessage should include(errorString)
  }

  they should "preserve the input order" in {
    runFirrtlTest(
      "CustomTransform",
      "/features",
      customTransforms = List(
        Dependency[FirstTransform],
        Dependency[SecondTransform],
        Dependency[ThirdTransform],
        Dependency[ReplaceExtModuleTransform]
      )
    )
  }

  they should "work if placed inside an object" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    node a = UInt<1>(0)
         |""".stripMargin
    val annotations = Seq(
      RunFirrtlTransformAnnotation(new Foo.A),
      FirrtlSourceAnnotation(input)
    )
    (new FirrtlStage).execute(Array.empty, annotations)
  }
}
