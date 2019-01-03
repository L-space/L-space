package lspace.librarian.process.traversal.step

import lspace.librarian.process.traversal._
import lspace.librarian.provider.detached.DetachedGraph
import lspace.librarian.provider.wrapped.WrappedNode
import lspace.librarian.structure._
import lspace.NS.types
import lspace.librarian.provider.mem.MemGraphDefault
import lspace.librarian.provider.mem.MemGraphDefault
import shapeless.HList

object Project
    extends StepDef("Project", "A project-step ..", () => Terminate.ontology :: Nil)
    with StepWrapper[Project] {

  def toStep(node: Node): Project =
    Project(
      node
        .out(Project.keys.byTraversal)
        .map(Traversal.toTraversal(_)(DetachedGraph)))

  object keys extends Terminate.Properties {
    object by
        extends Property.PropertyDef(
          lspace.NS.vocab.Lspace + "librarian/step/Project/by",
          "by",
          "A traversal ..",
          container = lspace.NS.types.`@list` :: Nil,
          `@range` = () => Traversal.ontology :: Nil
        )
    val byTraversal: TypedProperty[Node] = by.property + Traversal.ontology
  }
  override lazy val properties: List[Property] = keys.by :: Terminate.properties
  trait Properties extends Terminate.Properties

  implicit def toNode(project: Project): Node = {
    val node = DetachedGraph.nodes.create(ontology)
    project.by.map(_.toNode).foreach(node.addOut(keys.by, _))
    node
  }

}

case class Project(by: List[Traversal[_ <: ClassType[_], _ <: ClassType[_], _ <: HList]]) extends Terminate {

  lazy val toNode: Node            = this
  override def prettyPrint: String = "project(" + by.map(_.toString).map("_." + _).mkString(", ") + ")"
}
