package lspace.librarian.process.traversal.step

import lspace.librarian.process.traversal._
import lspace.librarian.provider.detached.DetachedGraph
import lspace.librarian.provider.mem.MemGraphDefault
import lspace.librarian.provider.mem.MemGraphDefault
import lspace.librarian.provider.wrapped.WrappedNode
import lspace.librarian.structure._

object OutV extends StepDef("OutV", "An outV-step ..", () => MoveStep.ontology :: Nil) with StepWrapper[OutV] {

  def wrap(node: Node): OutV = node match {
    case node: OutV => node
    case _          => OutV(node)
  }

  object keys extends MoveStep.Properties
  override lazy val properties: List[Property] = MoveStep.properties
  trait Properties extends MoveStep.Properties

  def apply(): OutV = {
    val node = DetachedGraph.nodes.create(ontology)

    OutV(node)
  }

}

case class OutV private (override val value: Node) extends WrappedNode(value) with MoveStep {
  override def prettyPrint: String = "outV"
}
