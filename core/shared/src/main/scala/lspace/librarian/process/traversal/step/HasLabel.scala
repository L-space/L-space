package lspace.librarian.process.traversal.step

import lspace.librarian.process.traversal._
import lspace.librarian.provider.detached.DetachedGraph
import lspace.librarian.provider.wrapped.WrappedNode
import lspace.librarian.structure._
import lspace.NS.types
import lspace.librarian.provider.mem.MemGraphDefault
import lspace.librarian.provider.mem.MemGraphDefault

object HasLabel
    extends StepDef("HasLabel", "A hasLabel-step filters resources by label.", () => HasStep.ontology :: Nil)
    with StepWrapper[HasLabel] {

  def wrap(node: Node): HasLabel = node match {
    case node: HasLabel => node
    case _              => HasLabel(node)
  }

  object keys {
    object label
        extends Property.PropertyDef(
          lspace.NS.vocab.Lspace + "librarian/step/HasLabel/Label",
          "Label",
          "A label",
          container = types.`@set` :: Nil,
          `@range` = () => Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil
        )
    val labelOntologyNode: TypedProperty[Node] = label.property + Ontology.ontology
    val labelPropertyNode: TypedProperty[Node] = label.property + Property.ontology
    val labelDataTypeNode: TypedProperty[Node] = label.property + DataType.ontology
  }
  override lazy val properties: List[Property] = keys.label :: HasStep.properties
  trait Properties extends HasStep.Properties {
    val label             = keys.label
    val labelOntologyNode = keys.labelOntologyNode
    val labelPropertyNode = keys.labelPropertyNode
    val labelDataTypeNode = keys.labelDataTypeNode
  }

  def apply[CT <: ClassType[_]](labels: List[CT]): HasLabel = {
    val node = DetachedGraph.nodes.create(ontology)

    labels.foreach {
      case ontology: Ontology => node.addOut(keys.label, ontology.asInstanceOf[Ontology])
      case property: Property => node.addOut(keys.label, property.asInstanceOf[Property])
      case classtype          => node.addOut(keys.label, classtype)
    }
    HasLabel(node)
  }
}

case class HasLabel private (override val value: Node) extends WrappedNode(value) with HasStep {
  def label: List[ClassType[_]] =
    out(HasLabel.keys.label)
      .collect {
        case node: Node => node
      }
      .flatMap(node => graph.ns.getClassType(node.iri))
      .collect {
        case ct if ct == DataType.ontology => DataType.default.`@datatype`
        case ct if ct == Ontology.ontology => DataType.default.`@class`
        case ct if ct == Property.ontology => DataType.default.`@property`
        case ct                            => ct
//TODO:           .getOrElse(throw new Exception("HasLabel with unknown/uncached ontology"))
      }
  override def prettyPrint: String = "hasLabel(" + label.map(_.iri).mkString(", ") + ")"
}
