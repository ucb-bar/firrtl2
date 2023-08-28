// SPDX-License-Identifier: Apache-2.0

package firrtlTests
package annotationTests

import firrtl2._
import firrtl2.testutils.{FirrtlFlatSpec, MakeCompiler}
import firrtl2.annotations.{Annotation, NoTargetAnnotation}
import firrtl2.options.Dependency
import firrtl2.stage.{FirrtlCircuitAnnotation, FirrtlStage, RunFirrtlTransformAnnotation}

case object FoundTargetDirTransformRanAnnotation extends NoTargetAnnotation
case object FoundTargetDirTransformFoundTargetDirAnnotation extends NoTargetAnnotation

/** Looks for [[TargetDirAnnotation]] */
class FindTargetDirTransform extends Transform {
  def execute(state: CircuitState): CircuitState = {
    val a: Option[Annotation] = state.annotations.collectFirst {
      case TargetDirAnnotation("a/b/c") => FoundTargetDirTransformFoundTargetDirAnnotation
    }
    state.copy(annotations = state.annotations ++ a ++ Some(FoundTargetDirTransformRanAnnotation))
  }
}

class TargetDirAnnotationSpec extends FirrtlFlatSpec {

  behavior.of("The target directory")

  val input =
    """circuit Top :
      |  module Top :
      |    input foo : UInt<32>
      |    output bar : UInt<32>
      |    bar <= foo
      """.stripMargin
  val targetDir = "a/b/c"

  it should "be available as an annotation when using execution options" in {
    val findTargetDir = new FindTargetDirTransform // looks for the annotation

    val annotations: Seq[Annotation] = (new FirrtlStage).execute(
      Array("--target-dir", targetDir, "--compiler", "high"),
      Seq(
        FirrtlCircuitAnnotation(Parser.parse(input)),
        RunFirrtlTransformAnnotation(findTargetDir)
      )
    )

    annotations should contain(FoundTargetDirTransformRanAnnotation)
    annotations should contain(FoundTargetDirTransformFoundTargetDirAnnotation)

    // Delete created directory
    val dir = new java.io.File(targetDir)
    dir.exists should be(true)
    FileUtils.deleteDirectoryHierarchy("a") should be(true)
  }

  it should "NOT be available as an annotation when using a raw compiler" in {
    val compiler = MakeCompiler.makeVerilogCompiler(Seq(Dependency[FindTargetDirTransform]))
    val circuit = Parser.parse(input.split("\n"))

    val annotations: Seq[Annotation] = compiler.transform(CircuitState(circuit, Seq())).annotations

    // Check that FindTargetDirTransform does not find the annotation
    annotations should contain(FoundTargetDirTransformRanAnnotation)
    annotations should not contain (FoundTargetDirTransformFoundTargetDirAnnotation)
  }
}
