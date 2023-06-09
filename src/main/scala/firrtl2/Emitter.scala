// SPDX-License-Identifier: Apache-2.0

package firrtl2

import java.io.File
import firrtl2.annotations.NoTargetAnnotation
import firrtl2.backends.experimental.smt.{Btor2Emitter, SMTLibEmitter}
import firrtl2.backends.experimental.rtlil.RtlilEmitter
import firrtl2.options.Viewer.view
import firrtl2.options.{CustomFileEmission, Dependency, HasShellOptions, PhaseException, ShellOption}
import firrtl2.passes.PassException
import firrtl2.stage.{FirrtlFileAnnotation, FirrtlOptions, RunFirrtlTransformAnnotation}
import firrtl2.stage.FirrtlOptionsView

case class EmitterException(message: String) extends PassException(message)

// ***** Annotations for telling the Emitters what to emit *****
sealed trait EmitAnnotation extends NoTargetAnnotation {
  val emitter: Class[_ <: Emitter]
}

case class EmitCircuitAnnotation(emitter: Class[_ <: Emitter]) extends EmitAnnotation

case class EmitAllModulesAnnotation(emitter: Class[_ <: Emitter]) extends EmitAnnotation

object EmitCircuitAnnotation extends HasShellOptions {
  val options = Seq(
    new ShellOption[String](
      longOption = "emit-circuit",
      toAnnotationSeq = (a: String) =>
        a match {
          case "chirrtl" =>
            Seq(RunFirrtlTransformAnnotation(new ChirrtlEmitter), EmitCircuitAnnotation(classOf[ChirrtlEmitter]))
          case "mhigh" =>
            Seq(
              RunFirrtlTransformAnnotation(new MinimumHighFirrtlEmitter),
              EmitCircuitAnnotation(classOf[MinimumHighFirrtlEmitter])
            )
          case "high" =>
            Seq(RunFirrtlTransformAnnotation(new HighFirrtlEmitter), EmitCircuitAnnotation(classOf[HighFirrtlEmitter]))
          case "middle" =>
            Seq(
              RunFirrtlTransformAnnotation(new MiddleFirrtlEmitter),
              EmitCircuitAnnotation(classOf[MiddleFirrtlEmitter])
            )
          case "low" =>
            Seq(RunFirrtlTransformAnnotation(new LowFirrtlEmitter), EmitCircuitAnnotation(classOf[LowFirrtlEmitter]))
          case "low-opt" =>
            Seq(
              RunFirrtlTransformAnnotation(Dependency(LowFirrtlOptimizedEmitter)),
              EmitCircuitAnnotation(LowFirrtlOptimizedEmitter.getClass)
            )
          case "verilog" | "mverilog" =>
            Seq(RunFirrtlTransformAnnotation(new VerilogEmitter), EmitCircuitAnnotation(classOf[VerilogEmitter]))
          case "sverilog" =>
            Seq(
              RunFirrtlTransformAnnotation(new SystemVerilogEmitter),
              EmitCircuitAnnotation(classOf[SystemVerilogEmitter])
            )
          case "experimental-btor2" | "btor2" =>
            Seq(RunFirrtlTransformAnnotation(Dependency(Btor2Emitter)), EmitCircuitAnnotation(Btor2Emitter.getClass))
          case "experimental-smt2" | "smt2" =>
            Seq(RunFirrtlTransformAnnotation(Dependency(SMTLibEmitter)), EmitCircuitAnnotation(SMTLibEmitter.getClass))
          case "experimental-rtlil" =>
            Seq(RunFirrtlTransformAnnotation(Dependency[RtlilEmitter]), EmitCircuitAnnotation(classOf[RtlilEmitter]))
          case _ => throw new PhaseException(s"Unknown emitter '$a'! (Did you misspell it?)")
        },
      helpText = "Run the specified circuit emitter (all modules in one file)",
      shortOption = Some("E"),
      // the experimental options are intentionally excluded from the help message
      helpValueName = Some("<chirrtl|high|middle|low|verilog|mverilog|sverilog>")
    )
  )
}

object EmitAllModulesAnnotation extends HasShellOptions {

  val options = Seq(
    new ShellOption[String](
      longOption = "emit-modules",
      toAnnotationSeq = (a: String) =>
        a match {
          case "chirrtl" =>
            Seq(RunFirrtlTransformAnnotation(new ChirrtlEmitter), EmitAllModulesAnnotation(classOf[ChirrtlEmitter]))
          case "mhigh" =>
            Seq(
              RunFirrtlTransformAnnotation(new MinimumHighFirrtlEmitter),
              EmitAllModulesAnnotation(classOf[MinimumHighFirrtlEmitter])
            )
          case "high" =>
            Seq(
              RunFirrtlTransformAnnotation(new HighFirrtlEmitter),
              EmitAllModulesAnnotation(classOf[HighFirrtlEmitter])
            )
          case "middle" =>
            Seq(
              RunFirrtlTransformAnnotation(new MiddleFirrtlEmitter),
              EmitAllModulesAnnotation(classOf[MiddleFirrtlEmitter])
            )
          case "low" =>
            Seq(RunFirrtlTransformAnnotation(new LowFirrtlEmitter), EmitAllModulesAnnotation(classOf[LowFirrtlEmitter]))
          case "verilog" | "mverilog" =>
            Seq(RunFirrtlTransformAnnotation(new VerilogEmitter), EmitAllModulesAnnotation(classOf[VerilogEmitter]))
          case "sverilog" =>
            Seq(
              RunFirrtlTransformAnnotation(new SystemVerilogEmitter),
              EmitAllModulesAnnotation(classOf[SystemVerilogEmitter])
            )
          case "experimental-rtlil" =>
            Seq(RunFirrtlTransformAnnotation(Dependency[RtlilEmitter]), EmitAllModulesAnnotation(classOf[RtlilEmitter]))
          case _ => throw new PhaseException(s"Unknown emitter '$a'! (Did you misspell it?)")
        },
      helpText = "Run the specified module emitter (one file per module)",
      shortOption = Some("e"),
      helpValueName = Some("<chirrtl|high|middle|low|verilog|mverilog|sverilog>")
    ),
    new ShellOption[String](
      longOption = "emission-options",
      toAnnotationSeq = s =>
        s.split(",")
          .map {
            case "disableRegisterRandomization" =>
              CustomDefaultRegisterEmission(useInitAsPreset = false, disableRandomization = true)
            case "disableMemRandomization" => CustomDefaultMemoryEmission(MemoryNoInit)
            case a                         => throw new PhaseException(s"Unknown emission options '$a'! (Did you misspell it?)")
          }
          .toSeq,
      helpText = "Options to disable random initialization for memory and registers",
      helpValueName = Some("<disableMemRandomization,disableRegisterRandomization>")
    )
  )

}

// ***** Annotations for results of emission *****
sealed abstract class EmittedComponent {
  def name: String

  def value: String

  def outputSuffix: String
}

sealed abstract class EmittedCircuit extends EmittedComponent

sealed abstract class EmittedModule extends EmittedComponent

/** Traits for Annotations containing emitted components */
trait EmittedAnnotation[T <: EmittedComponent] extends NoTargetAnnotation with CustomFileEmission {
  val value: T

  override protected def baseFileName(annotations: AnnotationSeq): String = {
    view[FirrtlOptions](annotations).outputFileName.getOrElse(value.name)
  }

  override protected val suffix: Option[String] = Some(value.outputSuffix)
}

sealed trait EmittedCircuitAnnotation[T <: EmittedCircuit] extends EmittedAnnotation[T] {
  override def getBytes = value.value.getBytes
}

sealed trait EmittedModuleAnnotation[T <: EmittedModule] extends EmittedAnnotation[T] {
  override def getBytes = value.value.getBytes
}

case class EmittedFirrtlModuleAnnotation(value: EmittedFirrtlModule)
    extends EmittedModuleAnnotation[EmittedFirrtlModule]
case class EmittedFirrtlCircuitAnnotation(value: EmittedFirrtlCircuit)
    extends EmittedCircuitAnnotation[EmittedFirrtlCircuit] {

  override def replacements(file: File): AnnotationSeq = Seq(FirrtlFileAnnotation(file.toString))
}

final case class EmittedFirrtlCircuit(name: String, value: String, outputSuffix: String) extends EmittedCircuit
final case class EmittedFirrtlModule(name: String, value: String, outputSuffix: String) extends EmittedModule

final case class EmittedVerilogCircuit(name: String, value: String, outputSuffix: String) extends EmittedCircuit
final case class EmittedVerilogModule(name: String, value: String, outputSuffix: String) extends EmittedModule
case class EmittedVerilogCircuitAnnotation(value: EmittedVerilogCircuit)
    extends EmittedCircuitAnnotation[EmittedVerilogCircuit]
case class EmittedVerilogModuleAnnotation(value: EmittedVerilogModule)
    extends EmittedModuleAnnotation[EmittedVerilogModule]
