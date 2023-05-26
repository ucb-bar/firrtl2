// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage

import firrtl2.transforms._
import firrtl2.passes.memlib._
import firrtl2.options.{HasShellOptions, ShellOption}
import firrtl2.annotations.MemorySynthInit

/**
  * This flag enables a set of options that guide the FIRRTL compilation flow to ultimately generate Verilog that is
  * more amenable to using for synthesized FPGA designs. Currently, this flag affects only memories, as the need to emit
  * memories that support downstream inference of hardened RAM macros. These options are not intended to be specialized
  * to any particular vendor; instead, they aim to emit simple Verilog that more closely reflects traditional
  * human-written definitions of synchronous-read memories.
  *
  * 1) Enable the [[firrtl2.passes.memlib.InferReadWrite]] transform to reduce port count, where applicable.
  *
  * 2) Use the [[firrtl2.transforms.SimplifyMems]] transform to Lower aggregate-typed memories with always-high masks to
  *    packed memories without splitting them into multiple independent ground-typed memories.
  *
  * 3) Use the [[firrtl2.passes.memlib.SeparateWriteClocks]] transform to ensure that each write port of a
  *    multiple-write, synchronous-read memory with 'undefined' collision behavior ultimately maps to a separate clocked
  *    process in the emitted Verilog. This avoids the issue of implicitly constraining cross-port collision and write
  *    ordering behavior and helps simplify inference of true dual-port RAM macros.
  *
  * 4) Use the [[firrtl2.passes.memlib.SetDefaultReadUnderWrite]] to specify that memories with undefined
  *    read-under-write behavior should map to emitted microarchitectures characteristic of "read-first" ports by
  *    default. This eliminates the difficulty of inferring a RAM macro that matches the strict semantics of
  *    "write-first" ports.
  *
  * 5) Add a [[firrtl2.passes.memlib.PassthroughSimpleSyncReadMemsAnnotation]] to allow some synchronous-read memories
  *    and readwrite ports to pass through [[firrtl2.passes.memlib.VerilogMemDelays]] without introducing explicit
  *    pipeline registers or splitting ports.
  *
  * 6) Add a [[firrtl2.annotations.MemorySynthInit]] to enable memory initialization values to be synthesized.
  */
object OptimizeForFPGA extends HasShellOptions {
  private val fpgaAnnos = Seq(
    InferReadWriteAnnotation,
    RunFirrtlTransformAnnotation(new InferReadWrite),
    RunFirrtlTransformAnnotation(new SeparateWriteClocks),
    DefaultReadFirstAnnotation,
    RunFirrtlTransformAnnotation(new SetDefaultReadUnderWrite),
    RunFirrtlTransformAnnotation(new SimplifyMems),
    PassthroughSimpleSyncReadMemsAnnotation,
    MemorySynthInit
  )
  val options = Seq(
    new ShellOption[Unit](
      longOption = "target:fpga",
      toAnnotationSeq = a => fpgaAnnos,
      helpText = "Choose compilation strategies that generally favor FPGA targets"
    )
  )
}
