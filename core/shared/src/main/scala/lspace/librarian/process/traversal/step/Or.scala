package lspace.librarian.process.traversal.step

import lspace.librarian.process.traversal._
import lspace.librarian.provider.detached.DetachedGraph
import lspace.librarian.provider.wrapped.WrappedNode
import lspace.librarian.structure._
import lspace.NS.types
import lspace.librarian.provider.mem.MemGraphDefault
import lspace.librarian.provider.mem.MemGraphDefault
import shapeless.{HList, HNil}

object Or
    extends StepDef("Or",
                    "An or-step traverser only survives if at least one of the n-traversals has a non-empty result.",
                    () => FilterStep.ontology :: Nil)
    with StepWrapper[Or] {

  def toStep(node: Node): Or = Or(
    node
      .out(keys.traversalTraversal)
      .map(
        Traversal
          .toTraversal(_)(DetachedGraph)
          .asInstanceOf[Traversal[ClassType[Any], ClassType[Any], HList]])
  )

  object keys extends FilterStep.Properties {
    object traversal
        extends Property.PropertyDef(
          lspace.NS.vocab.Lspace + "librarian/step/Or/traversal",
          "traversal",
          "A traversal ..",
          container = lspace.NS.types.`@list` :: Nil,
          `@range` = () => Traversal.ontology :: Nil
        )
    val traversalTraversal: TypedProperty[Node] = traversal.property + Traversal.ontology
  }
  override lazy val properties: List[Property] = keys.traversal :: FilterStep.properties
  trait Properties extends FilterStep.Properties {
    val traversal          = keys.traversal
    val traversalTraversal = keys.traversalTraversal
  }

  implicit def toNode(or: Or): Node = {
    val node = DetachedGraph.nodes.create(ontology)
    or.traversals.map(_.toNode).foreach(node.addOut(keys.traversal, _))
    node
  }

}

case class Or(traversals: List[Traversal[_, _, _ <: HList]]) extends FilterStep {

  lazy val toNode: Node = this
  override def prettyPrint: String =
    "or(" + traversals.map(_.toString).map("_." + _).mkString(", ") + ")"
}
