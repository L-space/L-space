package lspace.structure

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import lspace.NS
import lspace.datatype._
import lspace.structure.util.ClassTypeable
import monix.eval.{Coeval, Task}

import scala.collection.concurrent
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object Property {
  lazy val ontology: Ontology = {
    val ontology = new Ontology(
      NS.types.`@property`,
      Set(NS.types.`@property`, NS.types.rdfProperty, "https://schema.org/Property", "http://schema.org/Property"))
    ontology.iris.foreach(Ontology.ontologies.byIri.update(_, ontology))
    ontology
  }

//  lazy val unknownProperty: Ontology =
//    Ontology("@unknownProperty", iris = Set("@unknownProperty"), extendedClasses = List(ontology))

  lazy val empty: Property = Property("", iris = Set(""))

  implicit lazy val urlType: IriType[Property] = new IriType[Property] {
    val iri: String = NS.types.`@property`
  }

  implicit lazy val defaultProperty: ClassTypeable.Aux[Property, Property, IriType[Property]] =
    new ClassTypeable[Property] {
      type C  = Property
      type CT = IriType[Property]
      def ct: CT = urlType
    }

  object properties {
    object default {
      import Property.default._
      val properties = List(
        `@id`,
        `@ids`,
        `@container`, /*entry.iri -> entry, */
        `@range`,
        `@type`,
        `@extends`,
        inverseOf,
        `@properties`,
        `@language`,
        `@index`,
        `@label`,
        `@comment`,
        `@base`,
        `@value`,
        `@pvalue`,
        `@graph`,
        `@start`,
        `@end`,
        `@createdon`,
        `@modifiedon`,
        `@deletedon`,
        `@transcendedon`
//        `@valueRange`,
//        `@keyRange`,
//        `@ranges`
      )

      if (properties.size > 99) throw new Exception("extend default-property-id range!")
      val byId    = (100L to 100L + properties.size - 1).toList.zip(properties).toMap
      val byIri   = byId.toList.flatMap { case (id, p) => (p.iri :: p.iris.toList).map(_ -> p) }.toMap
      val idByIri = byId.toList.flatMap { case (id, p) => (p.iri :: p.iris.toList).map(_ -> id) }.toMap
    }
    private[lspace] val byIri: concurrent.Map[String, Property] =
      new ConcurrentHashMap[String, Property]().asScala

    def all: List[Property] = byIri.values.toList.distinct
    def get(iri: String, iris: Set[String] = Set()): Option[Property] = {
      val allIris = (iris + iri)
      allIris.flatMap(iri => default.byIri.get(iri).orElse(byIri.get(iri))).toList match {
        case List(property) => Some(property)
        case Nil            => None
        case properties =>
          scribe.warn(
            "It looks like multiple properties which have some @id's in common are found, this should not happen...")
          properties.headOption
      }
    }

    /**
      * This method is thread-safe and guarantees to return any existing property (if any) or it creates one.
      * TODO: add implicit resolver, default to offline resolver (not downloading linked data definition, no benefits of inheritance)
      * @param iri
      * @param iris
      * @return
      */
    def getOrCreate(iri: String, iris: Set[String] = Set()): Property = get(iri, iris).getOrElse {
      synchronized {
        get(iri, iris).getOrElse {
          val property = new Property(iri, iris + iri)
          property.iris.foreach(byIri.update(_, property))
//          property.extendedClasses.all() //force eagerly init of extended classes
//          property.properties()          //force eagerly init of associated properties
          property
        }
      }
    }
    def getAndUpdate(node: Node): Property = {
      if (node.hasLabel(Property.ontology).isEmpty)
        throw new Exception("cannot create Property from node without label @property")
      if (node.iri.isEmpty) throw new Exception("cannot create Property with empty iri")
      val property = getOrCreate(node.iri, node.iris)

      property.label ++ node
        .outE(Property.default.typed.labelString)
        .flatMap { edge =>
          val l = edge.out(Property.default.typed.languageString)
          if (l.nonEmpty) l.map(_ -> edge.to.value)
          else List("en"          -> edge.to.value)
        }
        .toMap
      property.comment ++ node
        .outE(Property.default.typed.commentString)
        .flatMap { edge =>
          val l = edge.out(Property.default.typed.commentString)
          if (l.nonEmpty) l.map(_ -> edge.to.value)
          else List("en"          -> edge.to.value)
        }
        .toMap
//      println(
//        node.iri + " @range " +
//          node
//            .out(Property.default.`@range`)
//            .collect { case node: Node => node.iri })
      property.range ++ node
        .out(Property.default.`@range`)
//        .headOption
        .collect {
          case nodes: List[_] =>
//            println(s"get range ${nodes.asInstanceOf[List[Node]].map(_.iri)} for ${node.iri}")
            nodes.collect {
              case node: Node if node.hasLabel(Ontology.ontology).orElse(node.hasLabel(Property.ontology)).isDefined =>
                ClassType.classtypes
                  .get(node.iri)
                  .getOrElse {
                    ClassType.classtypes.getAndUpdate(node)
                  } //orElse???
              case node: Node if ClassType.classtypes.get(node.iri).nonEmpty =>
                ClassType.classtypes.get(node.iri).get
              case node: Node =>
                if (node.iri.nonEmpty) {
//                  scribe.warn(s"range type without label, ${node.iri} is assumed to be an ontology")
                  Ontology.ontologies.getOrCreate(node.iri)
                } else throw new Exception(s"s node ${property.iri} with range iri ${node.iri} ${node.iris}")
              case iri: String =>
                ClassType.classtypes
                  .get(iri)
                  .getOrElse(throw new Exception("@range looks like an iri but cannot be wrapped by a classtype"))
            }
          case node: Node if node.hasLabel(Ontology.ontology).orElse(node.hasLabel(Property.ontology)).isDefined =>
            List(ClassType.classtypes.get(node.iri).getOrElse(ClassType.classtypes.getAndUpdate(node)))
          case node: Node if ClassType.classtypes.get(node.iri).nonEmpty =>
//            println(s"found ct by iri ${node.iri}")
            List(ClassType.classtypes.get(node.iri).get)
        }
        .toList
        .flatten

      property.properties ++ (node
        .out(Property.default.typed.propertyProperty)
        .filter(_.out("http://schema.org/supersededBy").isEmpty) ++ node
        .in(lspace.NS.types.schemaDomainIncludes, "http://schema.org/domainIncludes")
        .collect { case node: Node => node })
        .filter(_.labels.contains(Property.ontology))
        .filter(_.out("http://schema.org/supersededBy").isEmpty)
        .map(Property.properties.getAndUpdate)

      property.extendedClasses ++ node
        .out(Property.default.`@extends`)
//        .headOption
        .collect {
          case nodes: List[_] =>
            nodes.collect {
              case node: Node if node.hasLabel(Property.ontology).isDefined =>
                Property.properties
                  .get(node.iri, node.iris)
                  .getOrElse {
                    Property.properties.getAndUpdate(node)
                  } //orElse???
              case iri: String =>
                Property.properties
                  .get(iri)
                  .getOrElse(throw new Exception("@extends looks like an iri but cannot be wrapped by a property"))
            }
          case node: Node if node.hasLabel(Property.ontology).isDefined =>
            List(Property.properties.get(node.iri, node.iris).getOrElse(Property.properties.getAndUpdate(node)))
        }
        .toList
        .flatten

      node
        .out(Property.default.inverseOf)
        .headOption
        .collect {
          case nodes: List[_] =>
            nodes.collect {
              case node: Node if node.hasLabel(Property.ontology).isDefined =>
                Property.properties
                  .get(node.iri, node.iris)
                  .getOrElse {
                    Property.properties.getAndUpdate(node)
                  } //orElse???
              case iri: String =>
                Property.properties
                  .get(iri)
                  .getOrElse(throw new Exception("@extends looks like an iri but cannot be wrapped by a property"))
            }
          case node: Node if node.hasLabel(Property.ontology).isDefined =>
            List(Property.properties.get(node.iri, node.iris).getOrElse(Property.properties.getAndUpdate(node)))
        }
        .toList
        .flatten
        .headOption
        .map(property.inverseOf.set(_))

      property
    }

    def cached(long: Long): Option[Property] = default.byId.get(long)
  }

  object default {
    import DataType.default._

    val `@id`: Property = new Property(NS.types.`@id`) { rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess }
    val `@ids`: Property =
      new Property(NS.types.`@ids`, Set(NS.types.`@ids`, NS.types.schemaSameAs)) {
        rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess
      }
    val `@container`: Property =
      new Property(NS.types.`@container`) { rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess }
    val `@range`: Property = new Property(
      NS.types.`@range`,
      iris = Set(NS.types.`@range`, NS.types.schemaRange, "http://schema.org/rangeIncludes")) {
      rangeList = Coeval.delay(Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil).memoizeOnSuccess
    }
//      range = ListType(Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil) :: Nil
    val `@type`: Property = new Property(NS.types.`@type`, iris = Set(NS.types.`@type`, NS.types.rdfType)) {
      rangeList = Coeval.delay(Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil).memoizeOnSuccess
    }
//      range = Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil
    val `@extends`: Property = new Property(
      NS.types.`@extends`,
      iris = Set(NS.types.`@extends`, NS.types.rdfsSubClassOf, NS.types.rdfsSubPropertyOf)) {
      rangeList = Coeval
        .delay(ListType() :: Nil)
        .memoizeOnSuccess
    }
    val inverseOf: Property =
      new Property(NS.types.schemaInverseOf, Set(NS.types.schemaInverseOf, "http://schema.org/inverseOf"))
//      range = ListType(Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil) :: Nil
    val `@properties`: Property = new Property(NS.types.`@properties`) {
      rangeList = Coeval.delay(Property.ontology :: Nil).memoizeOnSuccess
    }
//    val `schema:domainIncludes`: Property =
//      new Property(NS.types.schemaDomainIncludes, iris = Set(NS.types.schemaDomainIncludes)) {
//        rangeList = Coeval.delay(Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil).memoizeOnSuccess
//        labelMap ++= Map("en" -> "domainIncludes")
//      }
    val `@language`: Property =
      new Property(NS.types.`@language`, iris = Set(NS.types.`@language`, NS.types.xsdLanguage)) {
        rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess
      }
    val `@index`: Property =
      new Property(NS.types.`@index`) { rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess }
    val `@label`: Property =
      new Property(NS.types.`@label`, iris = Set(NS.types.`@label`, NS.types.rdfsLabel)) {
        rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess
      }
    val `@comment`: Property =
      new Property(NS.types.`@comment`, iris = Set(NS.types.`@comment`, NS.types.rdfsComment)) {
        rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess
      }
    val `@base`: Property = new Property(NS.types.`@base`) {
      rangeList = Coeval.delay(`@string` :: Nil).memoizeOnSuccess
    }
    val `@value`: Property  = new Property(NS.types.`@value`)
    val `@pvalue`: Property = new Property(NS.types.`@pvalue`)
    val `@graph`: Property  = new Property(NS.types.`@graph`)
    val `@start`: Property = new Property(NS.types.`@start`) {
      rangeList = Coeval.delay(`@datetime` :: Nil).memoizeOnSuccess
    }
    val `@end`: Property = new Property(NS.types.`@end`) {
      rangeList = Coeval.delay(`@datetime` :: Nil).memoizeOnSuccess
    }
    val `@createdon`: Property = new Property(NS.types.`@createdon`) {
      rangeList = Coeval.delay(`@datetime` :: Nil).memoizeOnSuccess
    }
    val `@modifiedon`: Property =
      new Property(NS.types.`@modifiedon`) { rangeList = Coeval.delay(`@datetime` :: Nil).memoizeOnSuccess }
    val `@deletedon`: Property = new Property(NS.types.`@deletedon`) {
      rangeList = Coeval.delay(`@datetime` :: Nil).memoizeOnSuccess
    }
    val `@transcendedon`: Property =
      new Property(NS.types.`@transcendedon`) { rangeList = Coeval.delay(`@datetime` :: Nil).memoizeOnSuccess }
//    lazy val `@valueRange`: Property = CollectionType.keys.valueRange
//    lazy val `@keyRange`: Property   = MapType.keys.keyRange
//    lazy val `@ranges`: Property     = TupleType.keys.range

    object typed {
      lazy val iriUrlString: TypedProperty[String]    = `@id`.as(`@string`)
      lazy val irisUrlString: TypedProperty[String]   = `@ids`.as(`@string`)
      lazy val containerString: TypedProperty[String] = `@container`.as(`@string`)
      //  lazy val entryInt: TypedPropertyKey[Int] = entry as intType)
      lazy val rangeOntology: TypedProperty[Node] = `@range`.as(Ontology.ontology)
      lazy val rangeProperty: TypedProperty[Node] = `@range`.as(Property.ontology)
      lazy val rangeDataType: TypedProperty[Node] = `@range`.as(DataType.ontology)
//      lazy val rangeListClassType: TypedProperty[Node] = `@range` as ListType()

      lazy val typeOntology: TypedProperty[Node] = `@type`.as(Ontology.ontology) //Ontology.classType
      //  TYPE.addRange(ontology)
      lazy val typeProperty: TypedProperty[Node] = `@type`.as(Property.ontology) //Property.classType
      //  TYPE.addRange(property)
      lazy val typeDatatype: TypedProperty[Node] = `@type`.as(DataType.ontology) //as DataType.classType
      //  TYPE.addRange(datatype)
      lazy val extendsOntology
        : TypedProperty[List[Node]] = `@extends`.as(ListType(Ontology.ontology)) //as Ontology.classType
      lazy val extendsProperty
        : TypedProperty[List[Node]] = `@extends`.as(ListType(Property.ontology)) //as Property.classType
      lazy val extendsDataType
        : TypedProperty[List[Node]]                  = `@extends`.as(ListType(DataType.ontology)) //as DataType.classType
      lazy val propertyProperty: TypedProperty[Node] = `@properties`.as(Property.ontology) //as Property.classType
      lazy val languageString: TypedProperty[String] = `@language`.as(`@string`)
      lazy val indexString: TypedProperty[String]    = `@index`.as(`@string`)
      lazy val labelString: TypedProperty[String]    = `@label`.as(`@string`)
      lazy val commentString: TypedProperty[String]  = `@comment`.as(`@string`)
      lazy val baseString: TypedProperty[String]     = `@base`.as(`@string`)
      lazy val pvalueString: TypedProperty[String]   = `@pvalue`.as(`@string`)

      lazy val startDateTime: TypedProperty[Instant]         = `@start`.as(`@datetime`)
      lazy val endDateTime: TypedProperty[Instant]           = `@end`.as(`@datetime`)
      lazy val createdonDateTime: TypedProperty[Instant]     = `@createdon`.as(`@datetime`)
      lazy val modifiedonDateTime: TypedProperty[Instant]    = `@modifiedon`.as(`@datetime`)
      lazy val deletedonDateTime: TypedProperty[Instant]     = `@deletedon`.as(`@datetime`)
      lazy val transcendedOnDateTime: TypedProperty[Instant] = `@transcendedon`.as(`@datetime`)
//      lazy val valueListClassType: TypedProperty[List[Node]] = `@valueRange` as ListType(
//        Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil)
//      lazy val keyListClassType: TypedProperty[List[Node]] = `@keyRange` as ListType(
//        Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil)
//      lazy val rangesClasstype: TypedProperty[List[List[Node]]] = `@ranges` as ListType(
//        List(ListType(Ontology.ontology :: Property.ontology :: DataType.ontology :: Nil)))
    }
  }

  import default.typed._
  lazy val allTypedProperties: Map[String, TypedProperty[_]] = Map(
    iriUrlString.iri          -> iriUrlString,
    irisUrlString.iri         -> irisUrlString,
    containerString.iri       -> containerString,
    rangeOntology.iri         -> rangeOntology,
    rangeDataType.iri         -> rangeDataType,
    typeOntology.iri          -> typeOntology,
    typeProperty.iri          -> typeProperty,
    typeDatatype.iri          -> typeDatatype,
    extendsOntology.iri       -> extendsOntology,
    extendsProperty.iri       -> extendsProperty,
    extendsDataType.iri       -> extendsDataType,
    propertyProperty.iri      -> propertyProperty,
    languageString.iri        -> languageString,
    indexString.iri           -> indexString,
    labelString.iri           -> labelString,
    commentString.iri         -> commentString,
    baseString.iri            -> baseString,
    pvalueString.iri          -> pvalueString,
    startDateTime.iri         -> startDateTime,
    endDateTime.iri           -> endDateTime,
    createdonDateTime.iri     -> createdonDateTime,
    modifiedonDateTime.iri    -> modifiedonDateTime,
    deletedonDateTime.iri     -> deletedonDateTime,
    transcendedOnDateTime.iri -> transcendedOnDateTime
//    valueListClassType.iri    -> valueListClassType,
//    keyListClassType.iri      -> keyListClassType,
//    rangesClasstype.iri       -> rangesClasstype
  )

//  private def apply(iri: String,
//                    iris: Set[String] = Set(),
//                    range: () => List[ClassType[_]],
//                    label: Map[String, String] = Map(),
//                    comment: Map[String, String] = Map(),
//                    extendedClasses: () => List[Property] = () => List(),
//                    properties: () => List[Property] = () => List()): Property = {
//
//    def label0           = label
//    def comment0         = comment
//    def range0           = range
//    def extendedClasses0 = extendedClasses
//    def properties0      = properties
//
//    new Property(iri, iris + iri) {
//      labelMap ++= label0
//      commentMap = comment0
//      rangeList = Coeval.delay(range0()).memoizeOnSuccess
//      extendedClassesList = Coeval.delay(extendedClasses0()).memoizeOnSuccess
//      propertiesList = Coeval.delay(properties0().toSet).memoizeOnSuccess
//    }
//  }

  implicit def apply(iri: String): Property           = Property.properties.getOrCreate(iri, Set())
  def apply(iri: String, iris: Set[String]): Property = Property.properties.getOrCreate(iri, iris)
}

/**
  * //TODO: create inverse-link if any
  * @param iri
  * @param iris
  */
class Property(val iri: String, val iris: Set[String] = Set() //TODO: make updateable
) extends ClassType[Edge[Any, Any]] { self =>

  def as[T](range: ClassType[T]): TypedProperty[T] = TypedProperty(this, range)
//  def +[T](range: ClassType[T]): TypedProperty[T]  = as(range)

  protected var rangeList
    : Coeval[List[ClassType[Any]]] = Coeval(List()).memoizeOnSuccess //_range() ++ extendedClasses.flatMap(_.range) distinct

  object range {
    def apply(): List[ClassType[Any]]              = rangeList()
    def apply(iri: String): Option[ClassType[Any]] =
//      println(s"range find ${iri}")
      rangeList().find(_.iris.contains(iri)).orElse {
//        println(s"not found range ${iri} in ${apply().map(_.iris)}")
        var result: Option[ClassType[Any]] = None
        val oIt                            = extendedClasses().reverseIterator
        while (oIt.hasNext && result.isEmpty) {
          result = oIt.next().range(iri)
        }
        result
      }
    def +(range: => ClassType[Any]): this.type = this.synchronized {
      rangeList = rangeList.map { current =>
        val _range = range
        (current :+ _range).distinct
      }.memoizeOnSuccess
      this
    }
    def ++(range: => Iterable[ClassType[Any]]): this.type = this.synchronized {
      rangeList = rangeList.map { current =>
        (current ++ range).distinct
      }.memoizeOnSuccess
      this
    }
    def :=(range: => Iterable[ClassType[Any]]): this.type = this.synchronized {
      rangeList = Coeval(range.toList).memoizeOnSuccess
      this
    }
    def -(range: => ClassType[Any]): this.type = this.synchronized {
      rangeList = rangeList.map(_.filterNot(_ == range)).memoizeOnSuccess
      this
    }
    def --(range: => Iterable[ClassType[Any]]): this.type = this.synchronized {
      rangeList = rangeList.map(_.filterNot(range.toList.contains)).memoizeOnSuccess
      this
    }
  }
  def `@range` = range

  protected var extendedByClassesList: Coeval[List[Property]] = Coeval(List()).memoizeOnSuccess

  object extendedBy {
    def apply(): List[Property] = extendedByClassesList()
    def all(exclude: Set[Property] = Set()): Set[Property] = {
      val _extends = extendedByClassesList().toSet -- exclude
      _extends ++ (_extends - self).flatMap(_.extendedBy.all(_extends ++ exclude))
    }

    def +(child: => Property): this.type = this.synchronized {
      extendedByClassesList = extendedByClassesList.map { current =>
        val _child = child
        if (!current.contains(_child))
          (current :+ _child).distinct
        else {
          current
        }
      }.memoizeOnSuccess
      this
    }
  }
  protected var extendedClassesList
    : Coeval[List[Property]] = Coeval(List()).memoizeOnSuccess //_extendedClasses().filterNot(_.`extends`(this))

  object extendedClasses {
    type T = Property
    def apply(): List[Property] = extendedClassesList()

    /**
      *
      * @param exclude a property set to prevent circular recursion
      * recursively fetches all extended classes (parent of parents)
      * @return
      */
    def all(exclude: Set[Property] = Set()): Set[Property] = {
      val _extends = extendedClasses().toSet -- exclude
      _extends ++ (_extends - self).flatMap(_.extendedClasses.all(_extends ++ exclude))
    }
    def apply(iri: String): Boolean = {
      val _extends = extendedClasses().toSet
      _extends.exists(_.iris.contains(iri)) || (_extends - self)
        .filterNot(_.`extends`(self))
        .exists(_.extendedClasses(iri))
    }

    def +(parent: => Property): this.type = this.synchronized {
      extendedClassesList = extendedClassesList.map { current =>
        val _parent = parent
        if (!current.contains(_parent)) {
          _parent.extendedBy.+(self)
          (current :+ _parent).distinct
        } else {
          current
        }
      }.memoizeOnSuccess
      this
    }
    def ++(parent: => Iterable[Property]): this.type = this.synchronized {
      extendedClassesList = extendedClassesList.map { current =>
        parent.foreach(_.extendedBy.+(self))
        (current ++ parent).distinct
      }.memoizeOnSuccess
      this
    }
    def -(parent: => Property): this.type = this.synchronized {
      extendedClassesList = extendedClassesList.map(_.filterNot(_ == parent)).memoizeOnSuccess
      this
    }
    def --(parent: => Iterable[Property]): this.type = this.synchronized {
      extendedClassesList = extendedClassesList.map(_.filterNot(parent.toList.contains)).memoizeOnSuccess
      this
    }
  }

  protected var inverseOfOption
    : Coeval[Option[Property]] = Coeval(None).memoizeOnSuccess //_extendedClasses().filterNot(_.`extends`(this))

  object inverseOf {
    def apply(): Option[Property] = inverseOfOption()

    def get: Option[Property] = inverseOfOption.value()
    def set(inverse: => Property): this.type = this.synchronized {
      inverseOfOption = Coeval.now(Some(inverse)).memoizeOnSuccess
      this
    }
  }

  override def toString: String = s"property:$iri"

  override def equals(o: Any): Boolean = o match {
    case p: Property => iri == p.iri || iris.contains(p.iri)
    case n: Node     => iri == n.iri || iris.contains(n.iri)
    case _           => false
  }

  override def hashCode(): Int = iri.hashCode
}
