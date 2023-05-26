// SPDX-License-Identifier: Apache-2.0

package firrtl2

import firrtl2.annotations.Target
import firrtl2.annotations.TargetToken.{Instance, OfModule}
import firrtl2.analyses.InstanceKeyGraph
import firrtl2.testutils.FirrtlFlatSpec

class RenameMapPrivateSpec extends FirrtlFlatSpec {
  "RenameMap.fromInstanceRenames" should "handle instance renames" in {
    def tar(str: String): Target = Target.deserialize(str)
    val circuit = parse(
      """circuit Top :
        |  module Bar :
        |    skip
        |  module Foo :
        |    inst bar of Bar
        |  module Top :
        |    inst foo1 of Foo
        |    inst foo2 of Foo
        |    inst bar of Bar
        |""".stripMargin
    )
    val graph = InstanceKeyGraph(circuit)
    val renames = Map(
      OfModule("Foo") -> Map(Instance("bar") -> Instance("bbb")),
      OfModule("Top") -> Map(Instance("foo1") -> Instance("ffff"))
    )
    val rm = RenameMap.fromInstanceRenames(graph, renames)
    rm.get(tar("~Top|Top/foo1:Foo")) should be(Some(Seq(tar("~Top|Top/ffff:Foo"))))
    rm.get(tar("~Top|Top/foo2:Foo")) should be(None)
    // Check of nesting
    rm.get(tar("~Top|Top/foo1:Foo/bar:Bar")) should be(Some(Seq(tar("~Top|Top/ffff:Foo/bbb:Bar"))))
    rm.get(tar("~Top|Top/foo2:Foo/bar:Bar")) should be(Some(Seq(tar("~Top|Top/foo2:Foo/bbb:Bar"))))
    rm.get(tar("~Top|Foo/bar:Bar")) should be(Some(Seq(tar("~Top|Foo/bbb:Bar"))))
    rm.get(tar("~Top|Top/bar:Bar")) should be(None)
  }
}
