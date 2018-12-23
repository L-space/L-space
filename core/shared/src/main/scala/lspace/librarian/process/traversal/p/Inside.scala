package lspace.librarian.process.traversal.p

import lspace.librarian.process.traversal.P._
import lspace.librarian.process.traversal._
import lspace.librarian.process.traversal.helper.ClassTypeable
import lspace.librarian.provider.detached.DetachedGraph
import lspace.librarian.provider.wrapped.WrappedNode
import lspace.librarian.structure._

object Inside
    extends PredicateDef("Inside", `@extends` = () => List(RangeP.ontology))
    with PredicateWrapper[Inside[_]] {

  def wrap(node: Node): Inside[_] = node match {
    case node: Inside[_] => node
    case _ =>
      val (lower, helperLower) = OrderHelper map node.out(RangeP.keys.lower).head
      val (upper, helperUpper) = OrderHelper map node.out(RangeP.keys.upper).head
      new Inside(lower, upper, node)(helperLower)
  }

  object keys extends RangeP.Properties
  override lazy val properties: List[Property] = RangeP.properties
  trait Properties extends RangeP.Properties

  def apply[T: RangeHelper, T0, TT0 <: ClassType[_]](lower: T, upper: T)(
      implicit ct: ClassTypeable.Aux[T, T0, TT0]): Inside[T] = {
    val node = DetachedGraph.nodes.create(ontology)

    node.addOut(RangeP.keys.lower, lower)
    node.addOut(RangeP.keys.upper, upper)
    //    node.property(P.keys.dataTypeNode, dataType)
    new Inside(lower, upper, node)
  }

}

class Inside[T] private (val lower: T, val upper: T, override val value: Node)(implicit helper: OrderHelper[T])
    extends WrappedNode(value)
    with RangeP[T] {
  def assert(avalue: Any): Boolean = helper.gt(avalue, lower) && helper.lt(avalue, upper)

  override def prettyPrint: String = s"inside($lower, $upper)"
}
