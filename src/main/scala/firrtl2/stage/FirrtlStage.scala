// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage

import firrtl2.AnnotationSeq
import firrtl2.options.{Dependency, Phase, PhaseManager, Shell, Stage, StageMain}
import firrtl2.options.phases.DeletedWrapper
import firrtl2.stage.phases.CatchExceptions

class FirrtlPhase
    extends PhaseManager(
      targets = Seq(
        Dependency[firrtl2.stage.phases.Compiler]
      )
    ) {

  override def invalidates(a: Phase) = false

  override val wrappers = Seq(CatchExceptions(_: Phase), DeletedWrapper(_: Phase))

}

class FirrtlStage extends Stage {

  lazy val phase = new FirrtlPhase

  override def prerequisites = phase.prerequisites

  override def optionalPrerequisites = phase.optionalPrerequisites

  override def optionalPrerequisiteOf = phase.optionalPrerequisiteOf

  override def invalidates(a: Phase): Boolean = phase.invalidates(a)

  val shell: Shell = new Shell("firrtl2") with FirrtlCli

  override protected def run(annotations: AnnotationSeq): AnnotationSeq = phase.transform(annotations)

}

object FirrtlMain extends StageMain(new FirrtlStage)
