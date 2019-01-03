package lspace.librarian.structure.store

import lspace.librarian.structure._

object Store {}
trait Store[G <: Graph] {
  def iri: String
  val graph: G
  type T <: graph._Resource[_]
  type T2 <: graph._Resource[_]

  lazy val id: Long = iri.hashCode()

  def +(resource: T): Unit         = store(resource)
  def ++(resources: List[T]): Unit = store(resources)
  def store(resource: T): Unit
  def store(resources: List[T]): Unit

  def byId(id: Long): Option[T2]
  def byId(ids: List[Long]): Stream[T2]
  def byIri(iri: String): Stream[T2]

  def -(resource: T): Unit = delete(resource)
  def delete(resource: T): Unit
  def delete(resources: List[T]): Unit

  def all(): Stream[T2]
  def count(): Long
}
