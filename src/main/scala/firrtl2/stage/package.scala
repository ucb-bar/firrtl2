// SPDX-License-Identifier: Apache-2.0

package firrtl2

import firrtl2.annotations.DeletedAnnotation
import firrtl2.options.OptionsView
import logger.LazyLogging

/** The [[stage]] package provides an implementation of the FIRRTL compiler using the [[firrtl2.options]] package. This
  * primarily consists of:
  *   - [[FirrtlStage]], the internal and external (command line) interface to the FIRRTL compiler
  *   - A number of [[options.Phase Phase]]s that support and compartmentalize the individual operations of
  *     [[FirrtlStage]]
  *   - [[FirrtlOptions]], a class representing options that are necessary to drive the [[FirrtlStage]] and its
  *     [[firrtl2.options.Phase Phase]]s
  *   - [[FirrtlOptionsView]], a utility that constructs an [[options.OptionsView OptionsView]] of [[FirrtlOptions]]
  *     from an [[AnnotationSeq]]
  *   - [[FirrtlCli]], the command line options that the [[FirrtlStage]] supports
  */
package object stage {
  implicit object FirrtlOptionsView extends OptionsView[FirrtlOptions] with LazyLogging {

    /**
      * @todo custom transforms are appended as discovered, can this be prepended safely?
      */
    def view(options: AnnotationSeq): FirrtlOptions = options.collect { case a: FirrtlOption => a }
      .foldLeft(new FirrtlOptions()) { (c, x) =>
        x match {
          case OutputFileAnnotation(f)       => c.copy(outputFileName = Some(f))
          case InfoModeAnnotation(i)         => c.copy(infoModeName = i)
          case FirrtlCircuitAnnotation(cir)  => c.copy(firrtlCircuit = Some(cir))
          case WarnNoScalaVersionDeprecation => c
          case PrettyNoExprInlining          => c
          case _: DisableFold => c
          case AllowUnrecognizedAnnotations    => c
          case CurrentFirrtlStateAnnotation(a) => c
        }
      }
  }
}
