// SPDX-License-Identifier: Apache-2.0

package firrtl2.passes

import firrtl2.Utils.{create_exps, flow, get_field, get_valid_points, times, to_flip, to_flow}
import firrtl2.ir._
import firrtl2.options.Dependency
import firrtl2.{DuplexFlow, Flow, SinkFlow, SourceFlow, Transform, WDefInstance, WRef, WSubAccess, WSubField, WSubIndex}
import firrtl2.Mappers._

object ExpandConnects extends Pass {

  override def prerequisites =
    Seq(Dependency(PullMuxes), Dependency(ReplaceAccesses)) ++ firrtl2.stage.Forms.Deduped

  override def invalidates(a: Transform) = a match {
    case ResolveFlows => true
    case _            => false
  }

  def run(c: Circuit): Circuit = {
    def expand_connects(m: Module): Module = {
      val flows = collection.mutable.LinkedHashMap[String, Flow]()
      def expand_s(s: Statement): Statement = {
        def set_flow(e: Expression): Expression = e.map(set_flow) match {
          case ex: WRef => WRef(ex.name, ex.tpe, ex.kind, flows(ex.name))
          case ex: WSubField =>
            val f = get_field(ex.expr.tpe, ex.name)
            val flowx = times(flow(ex.expr), f.flip)
            WSubField(ex.expr, ex.name, ex.tpe, flowx)
          case ex: WSubIndex  => WSubIndex(ex.expr, ex.value, ex.tpe, flow(ex.expr))
          case ex: WSubAccess => WSubAccess(ex.expr, ex.index, ex.tpe, flow(ex.expr))
          case ex => ex
        }
        s match {
          case sx: DefWire      => flows(sx.name) = DuplexFlow; sx
          case sx: DefRegister  => flows(sx.name) = DuplexFlow; sx
          case sx: WDefInstance => flows(sx.name) = SourceFlow; sx
          case sx: DefMemory    => flows(sx.name) = SourceFlow; sx
          case sx: DefNode => flows(sx.name) = SourceFlow; sx
          case sx: IsInvalid =>
            val invalids = create_exps(sx.expr).flatMap {
              case expx =>
                flow(set_flow(expx)) match {
                  case DuplexFlow => Some(IsInvalid(sx.info, expx))
                  case SinkFlow   => Some(IsInvalid(sx.info, expx))
                  case _          => None
                }
            }
            invalids.size match {
              case 0 => EmptyStmt
              case 1 => invalids.head
              case _ => Block(invalids)
            }
          case sx: Connect =>
            val locs = create_exps(sx.loc)
            val exps = create_exps(sx.expr)
            Block(locs.zip(exps).map {
              case (locx, expx) =>
                to_flip(flow(locx)) match {
                  case Default => Connect(sx.info, locx, expx)
                  case Flip    => Connect(sx.info, expx, locx)
                }
            })
          case sx => sx.map(expand_s)
        }
      }

      m.ports.foreach { p => flows(p.name) = to_flow(p.direction) }
      Module(m.info, m.name, m.ports, expand_s(m.body))
    }

    val modulesx = c.modules.map {
      case (m: ExtModule) => m
      case (m: Module)    => expand_connects(m)
    }
    Circuit(c.info, modulesx, c.main)
  }
}
