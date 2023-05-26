// SPDX-License-Identifier: Apache-2.0

package firrtlTests.stage.phases

import firrtl2.ChirrtlEmitter
import firrtl2.annotations.Annotation
import firrtl2.stage.phases.AddDefaults
import firrtl2.transforms.BlackBoxTargetDirAnno
import firrtl2.stage.{InfoModeAnnotation, RunFirrtlTransformAnnotation}
import firrtl2.options.{Dependency, Phase, TargetDirAnnotation}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AddDefaultsSpec extends AnyFlatSpec with Matchers {

  class Fixture { val phase: Phase = new AddDefaults }

  behavior.of(classOf[AddDefaults].toString)

  it should "add expected default annotations and nothing else" in new Fixture {
    val expected = Seq(
      (a: Annotation) => a match { case BlackBoxTargetDirAnno(b) => b == TargetDirAnnotation().directory },
      (a: Annotation) =>
        a match {
          case RunFirrtlTransformAnnotation(e: firrtl2.Emitter) =>
            Dependency.fromTransform(e) == Dependency[firrtl2.VerilogEmitter]
        },
      (a: Annotation) => a match { case InfoModeAnnotation(b) => b == InfoModeAnnotation().modeName }
    )

    phase.transform(Seq.empty).zip(expected).map { case (x, f) => f(x) should be(true) }
  }

  it should "not overwrite existing annotations" in new Fixture {
    val input = Seq(
      BlackBoxTargetDirAnno("foo"),
      RunFirrtlTransformAnnotation(new ChirrtlEmitter),
      InfoModeAnnotation("ignore")
    )

    phase.transform(input).toSeq should be(input)
  }
}
