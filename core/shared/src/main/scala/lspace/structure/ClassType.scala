package lspace.structure

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}

import lspace.datatype._
import lspace.structure.util.ClassTypeable
import lspace.types.vector._
import monix.eval.{Coeval, Task}
import squants.time.Time

import scala.collection.immutable.ListSet

object ClassType {

  def valueToOntologyResource[T](value: T): DataType[T] = {
    (value match {
      case r: Node          => DataType.default.`@nodeURL`
      case r: Edge[_, _]    => DataType.default.`@edgeURL`
      case r: Value[_]      => DataType.default.`@valueURL`
      case r: Ontology      => DataType.default.`@class`
      case r: Property      => DataType.default.`@property`
      case r: DataType[_]   => DataType.default.`@datatype`
      case r: IriResource   => DataType.default.`@url`
      case v: String        => DataType.default.`@string`
      case v: Int           => DataType.default.`@int`
      case v: Double        => DataType.default.`@double`
      case v: Long          => DataType.default.`@long`
      case v: Instant       => DataType.default.`@datetime`
      case v: LocalDateTime => DataType.default.`@localdatetime`
      case v: LocalDate     => DataType.default.`@date`
      case v: LocalTime     => DataType.default.`@time`
      case v: Time          => DataType.default.`@duration`
      case v: Boolean       => DataType.default.`@boolean`
      case v: Geometry =>
        v match {
          case v: Point         => DataType.default.`@geopoint`
          case v: MultiPoint    => DataType.default.`@geomultipoint`
          case v: Line          => DataType.default.`@geoline`
          case v: MultiLine     => DataType.default.`@geomultiline`
          case v: Polygon       => DataType.default.`@geopolygon`
          case v: MultiPolygon  => DataType.default.`@geomultipolygon`
          case v: MultiGeometry => DataType.default.`@geomultigeo`
          case _                => DataType.default.`@geo`
        }
      case v: Map[_, _]    => DataType.default.mapType() //TODO: recursively map nested values to map -> toList.distinct ?
      case v: ListSet[_]   => DataType.default.listsetType()
      case v: List[_]      => DataType.default.listType()
      case v: Set[_]       => DataType.default.setType()
      case v: Vector[_]    => DataType.default.vectorType()
      case v: (_, _)       => TupleType(List(List(), List()))
      case v: (_, _, _)    => TupleType(List(List(), List(), List()))
      case v: (_, _, _, _) => TupleType(List(List(), List(), List(), List()))
      case _ =>
        throw new Exception(s"not a known range ${value.getClass}")
    }).asInstanceOf[DataType[T]]
  }

  object classtypes {
    def all: List[ClassType[_]] =
      List[ClassType[_]]() ++ Ontology.ontologies.all ++ Property.properties.all ++ DataType.datatypes.all
    def get(iri: String): Option[ClassType[_]] = {
      Ontology.ontologies
        .get(iri)
        .asInstanceOf[Option[ClassType[_]]]
        .orElse(Property.properties.get(iri))
        .orElse(DataType.datatypes.get(iri))
    }

    def getAndUpdate(node: Node): ClassType[Any] = {
      if (node.hasLabel(Ontology.ontology).nonEmpty) {
        if (node.hasLabel(DataType.ontology).nonEmpty) DataType.datatypes.getAndUpdate(node)
        else Ontology.ontologies.getAndUpdate(node)
      } else if (node.hasLabel(Property.ontology).nonEmpty) {
        Property.properties.getAndUpdate(node)
      } else {
        throw new Exception(s"${node.iri} does not look like a classtype ${node.labels
          .map(_.iri)} ${node.outE().map(e => e.key.iri + " " + e.to.prettyPrint)}")
      }
    }

//    def cached(iri: String): Option[ClassType[_]] =
//      Ontology.ontologies.cached(iri).orElse(Property.properties.cached(iri)).orElse(DataType.datatypes.cached(iri))
  }

  def makeT[T]: ClassType[T] = new ClassType[T] {

    val iri: String       = ""
    val iris: Set[String] = Set()

    //    val _extendedClasses: () => List[_ <: DataType[_]] = () => List()
    object extendedClasses {
      def apply(): List[ClassType[Any]] = List()
      def apply(iri: String): Boolean   = false
    }
//    val _properties: () => List[Property]              = () => List()
//    val base: Option[String] = None

    override def toString: String = s"classtype:$iri"
  }
  //helper, empty iri is used to recognize and filter out this classtype
  lazy val stubAny: ClassType[Any] = makeT[Any]

  //helper, empty iri is used to recognize and filter out this classtype
  lazy val stubNothing: ClassType[Nothing] = makeT[Nothing]

//  implicit def clsClasstype[T]: ClassTypeable.Aux[ClassType[T], T, ClassType[T]] = new ClassTypeable[ClassType[T]] {
//    type C  = T
//    type CT = ClassType[T]
//    def ct: CT = default[T]
//  }
}

/**
  *
  * @tparam T
  */
trait ClassType[+T] extends IriResource {

  def iris: Set[String] //TODO var iriList: Coeval[Set[String]]
  def `@ids` = iris

//  protected def _properties: () => List[Property]

//  protected def _extendedClasses: () => List[_ <: ClassType[_]]

  //  def extendedClasses: List[ClassType[_]] // = out(DataType.default.EXTENDS).collect { case node: Node => node }.map(ClassType.wrap).asInstanceOf[List[ClassType[_]]]
  /**
    * TODO: deprecate this method by implementing Ontology hierarchy
    * @param classType
    * @return
    */
  def `extends`(classType: ClassType[_]): Boolean = {
    if (extendedClasses().contains(classType)) true
    else {
      var result: Boolean = false
      val oIt             = extendedClasses().reverseIterator
      while (oIt.hasNext && !result) {
        result = oIt.next().`extends`(classType)
      }
      result
    }
  }
  def `@extends`(classType: ClassType[_]) = `extends`(classType)

  @deprecated(s"migrate to properties(iri: String)")
  def property(iri: String): Option[Property]    = properties(iri)
  def `@property`(iri: String): Option[Property] = properties(iri)

  protected var propertiesList
    : Coeval[Set[Property]] = Coeval.now(Set[Property]()).memoizeOnSuccess //_properties().toSet ++ extendedClasses.flatMap(_.properties)
  object properties {
    def apply(): Set[Property] = propertiesList()
    def apply(iri: String): Option[Property] = propertiesList().find(_.iris.contains(iri)).orElse {
      var result: Option[Property] = None
      val oIt                      = extendedClasses().reverseIterator
      while (oIt.hasNext && result.isEmpty) {
        result = oIt.next().properties(iri)
      }
      result
    }
    def +(property: Property): this.type = this.synchronized {
      propertiesList = propertiesList.map(_ + property).memoizeOnSuccess
      this
    }
    def ++(properties: Iterable[Property]): this.type = this.synchronized {
      propertiesList = propertiesList.map(_ ++ properties).memoizeOnSuccess
      this
    }
    def -(property: Property): this.type = this.synchronized {
      propertiesList = propertiesList.map(_ - property).memoizeOnSuccess
      this
    }
    def --(properties: Iterable[Property]): this.type = this.synchronized {
      propertiesList = propertiesList.map(_ -- properties).memoizeOnSuccess
      this
    }
  }
  def `@properties` = properties

  /**
    * TODO: create hash-tree for faster evaluation
    * @return
    */
//  def extendedClasses: List[ClassType[Any]] // = _extendedClasses()
//  trait Extends {
//    def apply(): List[ClassType[Any]]
//    def apply(iri: String): Boolean
//  }
  def extendedClasses: {
    def apply(): List[ClassType[Any]]
    def apply(iri: String): Boolean
  }

  protected var labelMap: Map[String, String] = Map()
  object label {
    def apply(): Map[String, String] = labelMap
    def apply(iri: String)           = labelMap.get(iri)
    def +(language: String = "en", label: String): this.type = this.synchronized {
      labelMap = labelMap + (language -> label)
      this
    }
    def ++(label: Map[String, String]): this.type = this.synchronized {
      labelMap = labelMap ++ label
      this
    }
  }
  def `@label` = label

  protected var commentMap: Map[String, String] = Map()
  object comment {
    def apply(): Map[String, String] = commentMap
    def apply(iri: String)           = commentMap.get(iri)
    def +(language: String = "en", comment: String): this.type = this.synchronized {
      commentMap = commentMap + (language -> comment)
      this
    }
    def ++(label: Map[String, String]): this.type = this.synchronized {
      commentMap = commentMap ++ label
      this
    }
  }
  def `@comment` = comment

//  def base: Option[String]
//  def `@base` = base
}
