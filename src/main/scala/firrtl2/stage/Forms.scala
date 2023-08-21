// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage

import firrtl2._
import firrtl2.backends.experimental.rtlil.RtlilEmitter
import firrtl2.backends.experimental.smt.{Btor2Emitter, SMTLibEmitter}
import firrtl2.options.Dependency
import firrtl2.stage.TransformManager.TransformDependency

/*
 * - InferWidths should have InferTypes split out
 * - ConvertFixedToSInt should have InferTypes split out
 * - Move InferTypes out of ZeroWidth
 */

object Forms {

  val ChirrtlForm: Seq[TransformDependency] = Seq.empty

  val MinimalHighForm: Seq[TransformDependency] = ChirrtlForm ++
    Seq(
      Dependency(passes.CheckChirrtl),
      Dependency(passes.CInferTypes),
      Dependency(passes.CInferMDir),
      Dependency(passes.RemoveCHIRRTL),
      Dependency[annotations.transforms.CleanupNamedTargets]
    )

  @deprecated("Use firrtl.stage.forms.MinimalHighForm", "FIRRTL 1.4.2")
  val WorkingIR: Seq[TransformDependency] = MinimalHighForm

  val Checks: Seq[TransformDependency] =
    Seq(
      Dependency(passes.CheckHighForm),
      Dependency(passes.CheckTypes),
      Dependency(passes.CheckFlows),
      Dependency(passes.CheckWidths)
    )

  val Resolved: Seq[TransformDependency] = MinimalHighForm ++ Checks ++
    Seq(
      Dependency(passes.ResolveKinds),
      Dependency(passes.InferTypes),
      Dependency(passes.ResolveFlows),
      Dependency[passes.InferWidths],
      Dependency[firrtl2.transforms.InferResets]
    )

  val Deduped: Seq[TransformDependency] = Resolved ++
    Seq(
      Dependency[firrtl2.transforms.DedupModules],
      Dependency[firrtl2.transforms.DedupAnnotationsTransform]
    )

  val HighForm: Seq[TransformDependency] = ChirrtlForm ++
    MinimalHighForm ++
    Resolved ++
    Deduped

  val MidForm: Seq[TransformDependency] = HighForm ++
    Seq(
      Dependency(passes.PullMuxes),
      Dependency(passes.ReplaceAccesses),
      Dependency(passes.ExpandConnects),
      Dependency(passes.RemoveAccesses),
      Dependency(passes.ZeroLengthVecs),
      Dependency[passes.ExpandWhensAndCheck],
      Dependency(passes.ZeroWidth),
      Dependency[firrtl2.transforms.formal.AssertSubmoduleAssumptions]
    )

  val LowForm: Seq[TransformDependency] = MidForm ++
    Seq(
      Dependency(passes.LowerTypes),
      Dependency(passes.LegalizeConnects),
      Dependency(firrtl2.transforms.RemoveReset),
      Dependency[firrtl2.transforms.CheckCombLoops],
      Dependency[checks.CheckResets],
      Dependency[firrtl2.transforms.RemoveWires]
    )

  val LowFormMinimumOptimized: Seq[TransformDependency] = LowForm ++
    Seq(
      Dependency(passes.RemoveValidIf),
      Dependency(passes.PadWidths),
      Dependency(passes.SplitExpressions)
    )

  val LowFormOptimized: Seq[TransformDependency] = LowFormMinimumOptimized ++
    Seq(
      Dependency[firrtl2.transforms.ConstantPropagation],
      Dependency(passes.CommonSubexpressionElimination),
      Dependency[firrtl2.transforms.DeadCodeElimination]
    )

  private def VerilogLowerings(optimize: Boolean): Seq[TransformDependency] = {
    Seq(
      Dependency(firrtl2.backends.verilog.LegalizeVerilog),
      Dependency(passes.memlib.VerilogMemDelays),
      Dependency[firrtl2.transforms.CombineCats]
    ) ++
      (if (optimize) Seq(Dependency[firrtl2.transforms.InlineBooleanExpressions]) else Seq()) ++
      Seq(
        Dependency[firrtl2.transforms.LegalizeAndReductionsTransform],
        Dependency[firrtl2.transforms.BlackBoxSourceHelper],
        Dependency[firrtl2.transforms.FixAddingNegativeLiterals],
        Dependency[firrtl2.transforms.ReplaceTruncatingArithmetic],
        Dependency[firrtl2.transforms.InlineBitExtractionsTransform],
        Dependency[firrtl2.transforms.InlineAcrossCastsTransform],
        Dependency[firrtl2.transforms.LegalizeClocksAndAsyncResetsTransform],
        Dependency[firrtl2.transforms.FlattenRegUpdate],
        Dependency[firrtl2.transforms.VerilogRename],
        Dependency(passes.VerilogPrep),
        Dependency[firrtl2.AddDescriptionNodes]
      )
  }

  val VerilogMinimumOptimized: Seq[TransformDependency] = LowFormMinimumOptimized ++ VerilogLowerings(optimize = false)

  val VerilogOptimized: Seq[TransformDependency] = LowFormOptimized ++ VerilogLowerings(optimize = true)

  val AssertsRemoved: Seq[TransformDependency] =
    Seq(
      Dependency(firrtl2.transforms.formal.ConvertAsserts),
      Dependency[firrtl2.transforms.formal.RemoveVerificationStatements]
    )

  val BackendEmitters =
    Seq(
      Dependency[VerilogEmitter],
      Dependency[MinimumVerilogEmitter],
      Dependency[SystemVerilogEmitter],
      Dependency(SMTLibEmitter),
      Dependency(Btor2Emitter),
      Dependency[RtlilEmitter]
    )

  val LowEmitters = Dependency[LowFirrtlEmitter] +: BackendEmitters

  val MidEmitters = Dependency[MiddleFirrtlEmitter] +: LowEmitters

  val HighEmitters = Dependency[HighFirrtlEmitter] +: MidEmitters

  val ChirrtlEmitters = Dependency[ChirrtlEmitter] +: HighEmitters

}
