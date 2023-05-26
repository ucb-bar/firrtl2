// SPDX-License-Identifier: Apache-2.0

package firrtlTests.stage

import firrtl2.stage.RunFirrtlTransformAnnotation
import firrtl2.options.Shell
import firrtl2.stage.FirrtlCli
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FirrtlCliSpec extends AnyFlatSpec with Matchers {

  behavior.of("FirrtlCli for RunFirrtlTransformAnnotation / -fct / --custom-transforms")

  it should "preserver transform order" in {
    val shell = new Shell("foo") with FirrtlCli
    val args = Array(
      "--custom-transforms",
      "firrtl2.transforms.BlackBoxSourceHelper,firrtl2.transforms.CheckCombLoops",
      "--custom-transforms",
      "firrtl2.transforms.CombineCats",
      "--custom-transforms",
      "firrtl2.transforms.ConstantPropagation"
    )
    val expected = Seq(
      classOf[firrtl2.transforms.BlackBoxSourceHelper],
      classOf[firrtl2.transforms.CheckCombLoops],
      classOf[firrtl2.transforms.CombineCats],
      classOf[firrtl2.transforms.ConstantPropagation]
    )

    shell
      .parse(args)
      .collect { case a: RunFirrtlTransformAnnotation => a }
      .zip(expected)
      .map { case (RunFirrtlTransformAnnotation(a), b) => a.getClass should be(b) }
  }

}
