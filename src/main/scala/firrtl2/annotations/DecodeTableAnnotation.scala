package firrtl2.annotations

/** DecodeTableAnnotation used to store a decode result for a specific `chisel3.util.experimental.decode.TruthTable`.
  * This is useful for saving large `TruthTable` during a elaboration time.
  *
  * @note user should manage the correctness of [[minimizedTable]].
  *
  * @param target output wire of a decoder.
  * @param truthTable input [[truthTable]] encoded in a serialized `TruthTable`.
  * @param minimizedTable minimized [[truthTable]] encoded in a serialized `TruthTable`.
  */
case class DecodeTableAnnotation(
  target:         ReferenceTarget,
  truthTable:     String,
  minimizedTable: String)
    extends SingleTargetAnnotation[ReferenceTarget] {
  override def duplicate(n: ReferenceTarget): Annotation = this.copy(target = n)
}
