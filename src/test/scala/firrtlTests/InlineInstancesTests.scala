// SPDX-License-Identifier: Apache-2.0

package firrtlTests

import firrtl2._
import firrtl2.annotations._
import firrtl2.passes.{InlineAnnotation, InlineInstances, PassExceptions, ResolveKinds}
import firrtl2.transforms.NoCircuitDedupAnnotation
import firrtl2.testutils._
import firrtl2.testutils.FirrtlCheckers._
import firrtl2.stage.TransformManager
import firrtl2.options.Dependency

/**
  * Tests inline instances transformation
  */
class InlineInstancesTests extends LowFirrtlTransformSpec(Seq(Dependency[InlineInstances])) {
  def inlineAnno(mod: String): Annotation = {
    val parts = mod.split('.')
    val modName = ModuleName(parts.head, CircuitName("Top")) // If this fails, bad input
    val name = if (parts.length == 1) modName else ComponentName(parts.tail.mkString("."), modName)
    InlineAnnotation(name)
  }

  def failingexecute(input: String, annotations: Seq[Annotation]): Exception =
    intercept[PassExceptions] { compile(input, annotations) }

  // Set this to debug, this will apply to all tests
  // Logger.setLevel(this.getClass, Debug)
  "The module Inline" should "be inlined" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of Inline
        |    i.a <= a
        |    b <= i.b
        |  module Inline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i_a : UInt<32>
        |    wire i_b : UInt<32>
        |    i_b <= i_a
        |    b <= i_b
        |    i_a <= a""".stripMargin
    execute(input, check, Seq(inlineAnno("Inline")))
  }

  "The all instances of Simple" should "be inlined" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i0 of Simple
        |    inst i1 of Simple
        |    i0.a <= a
        |    i1.a <= i0.b
        |    b <= i1.b
        |  module Simple :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i0_a : UInt<32>
        |    wire i0_b : UInt<32>
        |    i0_b <= i0_a
        |    wire i1_a : UInt<32>
        |    wire i1_b : UInt<32>
        |    i1_b <= i1_a
        |    b <= i1_b
        |    i0_a <= a
        |    i1_a <= i0_b""".stripMargin
    execute(input, check, Seq(inlineAnno("Simple")))
  }

  "Only one instance of Simple" should "be inlined" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i0 of Simple
        |    inst i1 of Simple
        |    i0.a <= a
        |    i1.a <= i0.b
        |    b <= i1.b
        |  module Simple :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i0_a : UInt<32>
        |    wire i0_b : UInt<32>
        |    i0_b <= i0_a
        |    inst i1 of Simple
        |    b <= i1.b
        |    i0_a <= a
        |    i1.a <= i0_b
        |  module Simple :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    execute(input, check, Seq(inlineAnno("Top.i0")))
  }

  "All instances of A" should "be inlined" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i0 of A
        |    inst i1 of B
        |    i0.a <= a
        |    i1.a <= i0.b
        |    b <= i1.b
        |  module A :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a
        |  module B :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of A
        |    i.a <= a
        |    b <= i.b""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i0_a : UInt<32>
        |    wire i0_b : UInt<32>
        |    i0_b <= i0_a
        |    inst i1 of B
        |    b <= i1.b
        |    i0_a <= a
        |    i1.a <= i0_b
        |  module B :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i_a : UInt<32>
        |    wire i_b : UInt<32>
        |    i_b <= i_a
        |    b <= i_b
        |    i_a <= a""".stripMargin
    execute(input, check, Seq(inlineAnno("A")))
  }

  "Non-inlined instances" should "still prepend prefix" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of A
        |    i.a <= a
        |    b <= i.b
        |  module A :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of B
        |    i.a <= a
        |    b <= i.b
        |  module B :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i_a : UInt<32>
        |    wire i_b : UInt<32>
        |    inst i_i of B
        |    i_b <= i_i.b
        |    i_i.a <= i_a
        |    b <= i_b
        |    i_a <= a
        |  module B :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    execute(input, check, Seq(inlineAnno("A")))
  }

  "A module with nested inlines" should "still prepend prefixes" in {
    val input =
      """|circuit Top:
         |  module Top:
         |    inst foo of Foo
         |  module Foo:
         |    inst bar of Bar
         |    inst baz of Bar
         |    node foo = UInt<1>("h0")
         |  module Bar:
         |    node bar = UInt<1>("h0")
         |""".stripMargin
    val check =
      """|circuit Top:
         |  module Top:
         |    node foo_bar_bar = UInt<1>("h0")
         |    inst foo_baz of Bar
         |    node foo_foo = UInt<1>("h0")
         |  module Bar:
         |    node bar = UInt<1>("h0")
         |""".stripMargin
    execute(input, check, Seq(inlineAnno("Foo"), inlineAnno("Foo.bar")))
  }

  "An inlined module" should "NOT be prefix unique" in {
    val input =
      """|circuit Top:
         |  module Top:
         |    inst a of A
         |    node a_foo = UInt<1>("h0")
         |    node a__bar = UInt<1>("h0")
         |  module A:
         |    node bar = UInt<1>("h0")
         |""".stripMargin
    val check =
      """|circuit Top:
         |  module Top:
         |    node a_bar = UInt<1>("h0")
         |    node a_foo = UInt<1>("h0")
         |    node a__bar = UInt<1>("h0")
         |""".stripMargin
    execute(input, check, Seq(inlineAnno("A")))
  }

  /* This test is mutually exclusive with the above */
  ignore should "be prefix unique" in {
    val input =
      """|circuit Top:
         |  module Top:
         |    inst a of A
         |    node a_foo = UInt<1>("h0")
         |    node a__bar = UInt<1>("h0")
         |  module A:
         |    node bar = UInt<1>("h0")
         |""".stripMargin
    val check =
      """|circuit Top:
         |  module Top:
         |    node a___bar = UInt<1>("h0")
         |    node a_foo = UInt<1>("h0")
         |    node a__bar = UInt<1>("h0")
         |""".stripMargin
    execute(input, check, Seq(inlineAnno("A")))
  }

  it should "uniquify sanely" in {
    val input =
      """|circuit Top:
         |  module Top:
         |    inst foo of Foo
         |    node foo_ = UInt<1>("h0")
         |    node foo__bar = UInt<1>("h0")
         |  module Foo:
         |    inst bar of Bar
         |    inst baz of Bar
         |    node foo = UInt<1>("h0")
         |  module Bar:
         |    node bar = UInt<1>("h0")
         |""".stripMargin
    val check =
      """|circuit Top:
         |  module Top:
         |    node foo__bar_bar = UInt<1>("h0")
         |    inst foo__baz of Bar
         |    node foo__foo = UInt<1>("h0")
         |    node foo_ = UInt<1>("h0")
         |    node foo__bar = UInt<1>("h0")
         |  module Bar:
         |    node bar = UInt<1>("h0")
         |""".stripMargin
    execute(input, check, Seq(inlineAnno("Foo"), inlineAnno("Foo.bar")))
  }

  // ---- Errors ----
  // 1) ext module
  "External module" should "not be inlined" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of A
        |    i.a <= a
        |    b <= i.b
        |  extmodule A :
        |    input a : UInt<32>
        |    output b : UInt<32>""".stripMargin
    failingexecute(input, Seq(inlineAnno("A")))
  }
  // 2) ext instance
  "External instance" should "not be inlined" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of A
        |    i.a <= a
        |    b <= i.b
        |  extmodule A :
        |    input a : UInt<32>
        |    output b : UInt<32>""".stripMargin
    failingexecute(input, Seq(inlineAnno("A")))
  }
  // 3) no module
  "Inlined module" should "exist" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    failingexecute(input, Seq(inlineAnno("A")))
  }
  // 4) no inst
  "Inlined instance" should "exist" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin
    failingexecute(input, Seq(inlineAnno("A")))
  }

  "Jack's Bug" should "not fail" in {

    val input = """circuit Top :
                  |  module Top :
                  |    input a : UInt<32>
                  |    output b : UInt<32>
                  |    inst i of Inline
                  |    i.a <= a
                  |    b <= i.b
                  |  module Inline :
                  |    input a : UInt<32>
                  |    output b : UInt<32>
                  |    inst child of InlineChild
                  |    child.a <= a
                  |    b <= child.b
                  |  module InlineChild :
                  |    input a : UInt<32>
                  |    output b : UInt<32>
                  |    b <= a""".stripMargin
    val check = """circuit Top :
                  |  module Top :
                  |    input a : UInt<32>
                  |    output b : UInt<32>
                  |    wire i_a : UInt<32>
                  |    wire i_b : UInt<32>
                  |    inst i_child of InlineChild
                  |    i_b <= i_child.b
                  |    i_child.a <= i_a
                  |    b <= i_b
                  |    i_a <= a
                  |  module InlineChild :
                  |    input a : UInt<32>
                  |    output b : UInt<32>
                  |    b <= a""".stripMargin
    execute(input, check, Seq(inlineAnno("Inline")))
  }

  case class DummyAnno(targets: CompleteTarget*) extends Annotation {
    override def update(renames: RenameMap): Seq[Annotation] = {
      Seq(DummyAnno(targets.flatMap { t =>
        renames.get(t).getOrElse(Seq(t))
      }: _*))
    }
  }
  "annotations" should "be renamed" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of Inline
        |    i.a <= a
        |    b <= i.b
        |  module Inline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst foo of NestedInline
        |    inst bar of NestedNoInline
        |    foo.a <= a
        |    bar.a <= foo.b
        |    b <= bar.b
        |  module NestedInline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a
        |  module NestedNoInline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a
        |""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i_a : UInt<32>
        |    wire i_b : UInt<32>
        |    wire i_foo_a : UInt<32>
        |    wire i_foo_b : UInt<32>
        |    i_foo_b <= i_foo_a
        |    inst i_bar of NestedNoInline
        |    i_b <= i_bar.b
        |    i_foo_a <= i_a
        |    i_bar.a <= i_foo_b
        |    b <= i_b
        |    i_a <= a
        |  module NestedNoInline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a
        |""".stripMargin
    val top = CircuitTarget("Top").module("Top")
    val inlined = top.instOf("i", "Inline")
    val nestedInlined = top.instOf("i", "Inline").instOf("foo", "NestedInline")
    val nestedNotInlined = top.instOf("i", "Inline").instOf("bar", "NestedNoInline")

    execute(
      input,
      check,
      Seq(
        inlineAnno("Inline"),
        inlineAnno("NestedInline"),
        NoCircuitDedupAnnotation,
        DummyAnno(inlined.ref("a")),
        DummyAnno(inlined.ref("b")),
        DummyAnno(nestedInlined.ref("a")),
        DummyAnno(nestedInlined.ref("b")),
        DummyAnno(nestedNotInlined.ref("a")),
        DummyAnno(nestedNotInlined.ref("b"))
      ),
      Seq(
        DummyAnno(top.ref("i_a")),
        DummyAnno(top.ref("i_b")),
        DummyAnno(top.ref("i_foo_a")),
        DummyAnno(top.ref("i_foo_b")),
        DummyAnno(top.instOf("i_bar", "NestedNoInline").ref("a")),
        DummyAnno(top.instOf("i_bar", "NestedNoInline").ref("b"))
      )
    )
  }

  "inlining named statements" should "work" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input clock : Clock
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of Inline
        |    i.clock <= clock
        |    i.a <= a
        |    b <= i.b
        |  module Inline :
        |    input clock : Clock
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a
        |    assert(clock, UInt(1), eq(a,b), "a == b") : assert1
        |    assert(clock, UInt(1), not(eq(a,b)), "a != b")
        |    stop(clock, UInt(0), 0)
        |""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input clock : Clock
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i_clock : Clock
        |    wire i_a : UInt<32>
        |    wire i_b : UInt<32>
        |    i_b <= i_a
        |    assert(i_clock, UInt(1), eq(i_a, i_b), "a == b") : i_assert1
        |    assert(i_clock, UInt(1), not(eq(i_a, i_b)), "a != b")
        |    stop(i_clock, UInt(0), 0)
        |    b <= i_b
        |    i_clock <= clock
        |    i_a <= a
        |""".stripMargin
    val top = CircuitTarget("Top").module("Top")
    val inlined = top.instOf("i", "Inline")

    execute(
      input,
      check,
      Seq(
        inlineAnno("Inline"),
        NoCircuitDedupAnnotation,
        DummyAnno(inlined.ref("assert1"))
      ),
      Seq(
        DummyAnno(top.ref("i_assert1"))
      )
    )
  }

  "inlining both grandparent and grandchild" should "should work" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of Inline
        |    i.a <= a
        |    b <= i.b
        |  module Inline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst foo of NestedInline
        |    inst bar of NestedNoInline
        |    foo.a <= a
        |    bar.a <= foo.b
        |    b <= bar.b
        |  module NestedInline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a
        |  module NestedNoInline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst foo of NestedInline
        |    foo.a <= a
        |    b <= foo.b
        |""".stripMargin
    val check =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire i_a : UInt<32>
        |    wire i_b : UInt<32>
        |    wire i_foo_a : UInt<32>
        |    wire i_foo_b : UInt<32>
        |    i_foo_b <= i_foo_a
        |    inst i_bar of NestedNoInline
        |    i_b <= i_bar.b
        |    i_foo_a <= i_a
        |    i_bar.a <= i_foo_b
        |    b <= i_b
        |    i_a <= a
        |  module NestedNoInline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    wire foo_a : UInt<32>
        |    wire foo_b : UInt<32>
        |    foo_b <= foo_a
        |    b <= foo_b
        |    foo_a <= a
        |""".stripMargin
    val top = CircuitTarget("Top").module("Top")
    val inlined = top.instOf("i", "Inline")
    val nestedInlined = inlined.instOf("foo", "NestedInline")
    val nestedNotInlined = inlined.instOf("bar", "NestedNoInline")
    val innerNestedInlined = nestedNotInlined.instOf("foo", "NestedInline")

    val inlineModuleTarget = top.copy(module = "Inline")
    val nestedInlineModuleTarget = top.copy(module = "NestedInline")

    execute(
      input,
      check,
      Seq(
        inlineAnno("Inline"),
        inlineAnno("NestedInline"),
        DummyAnno(inlined.ref("a")),
        DummyAnno(inlined.ref("b")),
        DummyAnno(nestedInlined.ref("a")),
        DummyAnno(nestedInlined.ref("b")),
        DummyAnno(nestedNotInlined.ref("a")),
        DummyAnno(nestedNotInlined.ref("b")),
        DummyAnno(innerNestedInlined.ref("a")),
        DummyAnno(innerNestedInlined.ref("b")),
        DummyAnno(inlineModuleTarget.instOf("bar", "NestedNoInline")),
        DummyAnno(inlineModuleTarget.ref("a"), inlineModuleTarget.ref("b")),
        DummyAnno(nestedInlineModuleTarget.ref("a"))
      ),
      Seq(
        DummyAnno(top.ref("i_a")),
        DummyAnno(top.ref("i_b")),
        DummyAnno(top.ref("i_foo_a")),
        DummyAnno(top.ref("i_foo_b")),
        DummyAnno(top.instOf("i_bar", "NestedNoInline").ref("a")),
        DummyAnno(top.instOf("i_bar", "NestedNoInline").ref("b")),
        DummyAnno(top.instOf("i_bar", "NestedNoInline").ref("foo_a")),
        DummyAnno(top.instOf("i_bar", "NestedNoInline").ref("foo_b")),
        DummyAnno(top.instOf("i_bar", "NestedNoInline")),
        DummyAnno(top.ref("i_a"), top.ref("i_b")),
        DummyAnno(top.ref("i_foo_a"), top.copy(module = "NestedNoInline").ref("foo_a"))
      )
    )
  }

  "InlineInstances" should "properly invalidate ResolveKinds" in {
    val input =
      """circuit Top :
        |  module Top :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    inst i of Inline
        |    i.a <= a
        |    b <= i.b
        |  module Inline :
        |    input a : UInt<32>
        |    output b : UInt<32>
        |    b <= a""".stripMargin

    val state = CircuitState(parse(input), Seq(inlineAnno("Inline")))
    val manager = new TransformManager(Seq(Dependency[InlineInstances], Dependency(ResolveKinds)))
    val result = manager.execute(state)

    result shouldNot containTree { case WRef("i_a", _, PortKind, _) => true }
    result should containTree { case WRef("i_a", _, WireKind, _) => true }
  }
}
