package lspace.datatype

import lspace.NS
import lspace.structure.util.ClassTypeable
import lspace.structure.Property

object IntType extends DataTypeDef[IntType[Int]] {

  lazy val datatype: IntType[Int] = new IntType[Int] {
    val iri: String = NS.types.`@int`
    override val iris: Set[String] =
      Set(NS.types.`@int`, NS.types.schemaInteger, "http://schema.org/Integer", NS.types.xsdInt)
    labelMap ++= Map("en" -> NS.types.`@int`)
    override lazy val _extendedClasses: List[_ <: DataType[_]] = List(NumericType.datatype)
  }

  object keys extends NumericType.Properties
  override lazy val properties: List[Property] = NumericType.properties
  trait Properties extends NumericType.Properties

  implicit val defaultIntType: ClassTypeable.Aux[IntType[Int], Int, IntType[Int]] =
    new ClassTypeable[IntType[Int]] {
      type C  = Int
      type CT = IntType[Int]
      def ct: CT = datatype
    }
}

trait IntType[+T] extends NumericType[T]
