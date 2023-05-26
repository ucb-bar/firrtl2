// SPDX-License-Identifier: Apache-2.0

package firrtl2.stage

import firrtl2.options.Shell
import firrtl2.passes.CommonSubexpressionElimination
import firrtl2.transforms.{CustomRadixTransform, NoCircuitDedupAnnotation}

/** [[firrtl2.options.Shell Shell]] mixin that provides command line options for FIRRTL. This does not include any
  * [[firrtl2.options.RegisteredLibrary RegisteredLibrary]] or [[firrtl2.options.RegisteredTransform RegisteredTransform]]
  * as those are automatically loaded by the [[firrtl2.options.Stage Stage]] using this [[firrtl2.options.Shell Shell]].
  */
trait FirrtlCli { this: Shell =>
  parser.note("FIRRTL Compiler Options")
  Seq(
    FirrtlFileAnnotation,
    OutputFileAnnotation,
    InfoModeAnnotation,
    FirrtlSourceAnnotation,
    RunFirrtlTransformAnnotation,
    firrtl2.EmitCircuitAnnotation,
    firrtl2.EmitAllModulesAnnotation,
    NoCircuitDedupAnnotation,
    PrettyNoExprInlining,
    OptimizeForFPGA,
    CurrentFirrtlStateAnnotation,
    CommonSubexpressionElimination,
    AllowUnrecognizedAnnotations,
    CustomRadixTransform
  )
    .map(_.addOptions(parser))

  phases.DriverCompatibility.TopNameAnnotation.addOptions(parser)
  phases.DriverCompatibility.EmitOneFilePerModuleAnnotation.addOptions(parser)
}
