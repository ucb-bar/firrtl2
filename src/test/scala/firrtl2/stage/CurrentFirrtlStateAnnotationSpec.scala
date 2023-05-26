// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage

import firrtl2.options.{Dependency, ShellOption}
import firrtl2.stage.transforms.Compiler
import firrtl2.stage.TransformManager.TransformDependency
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CurrentFirrtlStateAnnotationSpec extends AnyFlatSpec with Matchers {

  def getTransforms(input: String): Seq[TransformDependency] = {
    val shellOption: ShellOption[String] = CurrentFirrtlStateAnnotation.options.head
    val currentState = shellOption
      .toAnnotationSeq(input)
      .collectFirst {
        case CurrentFirrtlStateAnnotation(currentState) => currentState
      }
      .get
    new Compiler(Forms.VerilogOptimized, currentState).flattenedTransformOrder.map(Dependency.fromTransform)
  }

  behavior.of("CurrentFirrtlStateAnnotation")

  it should "produce an expected transform order for CHIRRTL -> Verilog" in {
    getTransforms("chirrtl") should contain(Dependency(firrtl2.passes.CheckChirrtl))
  }

  it should "produce an expected transform order for minimum high FIRRTL -> Verilog" in {
    val transforms = getTransforms("mhigh")
    transforms should not contain noneOf(Dependency(firrtl2.passes.CheckChirrtl), Dependency(firrtl2.passes.InferTypes))
    transforms should contain(Dependency(firrtl2.passes.CheckHighForm))
  }

  it should "produce an expected transform order for high FIRRTL -> Verilog" in {
    val transforms = getTransforms("high")
    transforms should not contain (Dependency[firrtl2.transforms.DedupModules])
    (transforms should contain).allOf(
      Dependency(firrtl2.passes.InferTypes),
      Dependency[firrtl2.passes.ExpandWhensAndCheck]
    )
  }

  it should "produce an expected transform order for middle FIRRTL -> Verilog" in {
    val transforms = getTransforms("middle")
    transforms should not contain (Dependency[firrtl2.passes.ExpandWhensAndCheck])
    (transforms should contain).allOf(Dependency(firrtl2.passes.InferTypes), Dependency(firrtl2.passes.LowerTypes))
  }

  it should "produce an expected transform order for low FIRRTL -> Verilog" in {
    val transforms = getTransforms("low")
    transforms should not contain (Dependency(firrtl2.passes.LowerTypes))
    (transforms should contain).allOf(
      Dependency(firrtl2.passes.InferTypes),
      Dependency(firrtl2.passes.CommonSubexpressionElimination)
    )
  }

  it should "produce an expected transform order for optimized low FIRRTL -> Verilog" in {
    val transforms = getTransforms("low-opt")
    transforms should not contain (Dependency(firrtl2.passes.CommonSubexpressionElimination))
    (transforms should contain).allOf(
      Dependency(firrtl2.passes.InferTypes),
      Dependency[firrtl2.transforms.VerilogRename]
    )
  }

}
