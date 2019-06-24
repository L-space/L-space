package lspace.librarian

import lspace.datatype._
import lspace.librarian.traversal.step._
import lspace.structure._
import lspace.structure.util._
import monix.reactive.Observable
import shapeless.ops.hlist.{Collect, Reverse}
import shapeless.{Path => _, Segment => _, _}
import shapeless.{::, DepFn2, HList, HNil, Lazy, Poly2}

import scala.annotation.implicitNotFound

package object traversal {

  type TypedKey[Z] = TypedProperty[Z]

  object ContainerSteps extends Poly1 {
    implicit def count                        = at[Count](s => s)
    implicit def head                         = at[Head](s => s)
    implicit def last                         = at[Last](s => s)
    implicit def max                          = at[Max](s => s)
    implicit def min                          = at[Min](s => s)
    implicit def mean                         = at[Mean](s => s)
    implicit def sum                          = at[Sum](s => s)
    implicit def project[Traversals <: HList] = at[Project[Traversals]](s => s)
    implicit def group[T <: ClassType[_], Segments <: HList, Tv <: ClassType[_], SegmentsV <: HList] =
      at[Group[T, Segments, Tv, SegmentsV]](s => s)
    //  implicit def caseMap[T <: MapStep] = at[T](s => s)
    implicit def outmap                                      = at[OutMap](s => s)
    implicit def outemap                                     = at[OutEMap](s => s)
    implicit def inmap                                       = at[InMap](s => s)
    implicit def inemap                                      = at[InEMap](s => s)
    implicit def is                                          = at[Is](s => s)
    implicit def path[ET <: ClassType[_], Segments <: HList] = at[Path[ET, Segments]](s => s)
  }

  object LabelSteps extends Poly1 {
    implicit def as[T, name <: String] = at[As[T, name]](s => s)
  }

  object LabelStepTypes extends Poly1 {
    implicit def getType[T, name <: String] = at[As[T, name]](t => t._maphelper)
  }

//  object LabelStepToKeyValueLabelStep extends Poly1 {
//    implicit def getType[T, name] = at[As[T, name]](t => t._maphelper ->)
//  }

  trait SelectorSelecter[T <: HList] extends DepFn1[T] {
    type Out <: Selector[T, HNil]
  }
  object SelectorSelecter {
    import shapeless.ops.hlist.{Selector => _, _}

    //    def apply[L <: HList](implicit selector: SelectorSelecter[L]): Aux[L, selector.Out] = selector

    type Aux[In <: HList, Out0 <: Selector[_, HNil]] = SelectorSelecter[In] { type Out = Out0 }

    implicit def selector3Selecter[A, nameA <: String, B, nameB <: String, C, nameC <: String]
      : Aux[As[C, nameC] :: As[B, nameB] :: As[A, nameA] :: HNil, Selector3[A, nameA, B, nameB, C, nameC, HNil]] =
      new SelectorSelecter[As[C, nameC] :: As[B, nameB] :: As[A, nameA] :: HNil] {
        type Out = Selector3[A, nameA, B, nameB, C, nameC, HNil]
        def apply(l: As[C, nameC] :: As[B, nameB] :: As[A, nameA] :: HNil) =
          Selector3.apply(l, HNil)
      }
    implicit def selector2Selecter[A, nameA <: String, B, nameB <: String]
      : Aux[As[B, nameB] :: As[A, nameA] :: HNil, Selector2[A, nameA, B, nameB, HNil]] =
      new SelectorSelecter[As[B, nameB] :: As[A, nameA] :: HNil] {
        type Out = Selector2[A, nameA, B, nameB, HNil]
        def apply(l: As[B, nameB] :: As[A, nameA] :: HNil) = Selector2.apply(l, HNil)
      }

    implicit def selector1Selecter[A, nameA <: String]: Aux[As[A, nameA] :: HNil, Selector1[A, nameA, HNil]] =
      new SelectorSelecter[As[A, nameA] :: HNil] {
        type Out = Selector1[A, nameA, HNil]
        def apply(l: As[A, nameA] :: HNil) = Selector1.apply(l, HNil)
      }

    implicit def selector0Selecter: Aux[HNil, Selector0[HNil]] = new SelectorSelecter[HNil] {
      type Out = Selector0[HNil]
      def apply(l: HNil): Out = Selector0(l, HNil)
    }
  }

  /**
    * https://stackoverflow.com/questions/25713668/do-a-covariant-filter-on-an-hlist
    * @tparam L
    * @tparam U
    */
  @implicitNotFound(
    "Implicit not found: lspace.librarian.traversal.CoFilter[${L}, ${U}]. You requested to filter all elements of type <: ${U}, but there is none in the HList ${L}.")
  trait CoFilter[L <: HList, U] extends DepFn1[L] { type Out <: HList }

  object CoFilter {
    def apply[L <: HList, U](implicit f: CoFilter[L, U]): Aux[L, U, f.Out] = f

    type Aux[L <: HList, U, Out0 <: HList] = CoFilter[L, U] { type Out = Out0 }

    implicit def hlistCoFilterHNil[L <: HList, U]: Aux[HNil, U, HNil] =
      new CoFilter[HNil, U] {
        type Out = HNil
        def apply(l: HNil): Out = HNil
      }

    implicit def hlistCoFilter1[U, H <: U, T <: HList](implicit f: CoFilter[T, U]): Aux[H :: T, U, H :: f.Out] =
      new CoFilter[H :: T, U] {
        type Out = H :: f.Out
        def apply(l: H :: T): Out = l.head :: f(l.tail)
      }

    implicit def hlistCoFilter2[U, H, T <: HList](implicit f: CoFilter[T, U], e: H <:!< U): Aux[H :: T, U, f.Out] =
      new CoFilter[H :: T, U] {
        type Out = f.Out
        def apply(l: H :: T): Out = f(l.tail)
      }
  }

  implicit final class HListOps[L <: HList](val l: L) {
    def covariantFilter[U](implicit filter: CoFilter[L, U]): filter.Out = filter(l)
  }

  private def toTuple2[Prefix, Suffix](l: Prefix :: Suffix :: HNil): (Prefix, Suffix) = (l.head, l.tail.head)

  @implicitNotFound(
    "Implicit not found: lspace.librarian.traversal.CoSplitLeft[${L}, ${U}]. You requested to split at an element of type <: ${U}, but there is none in the HList ${L}.")
  trait CoSplitLeft[L <: HList, U] extends DepFn1[L] with Serializable {
    type Prefix <: HList
    type Suffix <: HList
    type Out = (Prefix, Suffix)

    def apply(l: L): Out = toTuple2(product(l))
    def product(l: L): Prefix :: Suffix :: HNil
  }

  object CoSplitLeft {
    def apply[L <: HList, U](implicit split: CoSplitLeft[L, U]): Aux[L, U, split.Prefix, split.Suffix] = split

    type Aux[L <: HList, U, Prefix0 <: HList, Suffix0 <: HList] = CoSplitLeft[L, U] {
      type Prefix = Prefix0
      type Suffix = Suffix0
    }

    implicit def splitLeft[L <: HList, U, P <: HList, S <: HList](
        implicit splitLeft: CoSplitLeft0[HNil, L, U, P, S]): Aux[L, U, P, S] =
      new CoSplitLeft[L, U] {
        type Prefix = P
        type Suffix = S

        def product(l: L): Prefix :: Suffix :: HNil = splitLeft(HNil, l)
      }

    trait CoSplitLeft0[AccP <: HList, AccS <: HList, U, P <: HList, S <: HList] extends Serializable {
      def apply(accP: AccP, accS: AccS): P :: S :: HNil
    }

    trait LowPrioritySplitLeft0 {
      implicit def hlistSplitLeft1[AccP <: HList, AccSH, AccST <: HList, U, P <: HList, S <: HList](
          implicit slt: CoSplitLeft0[AccP, AccST, U, P, S]): CoSplitLeft0[AccP, AccSH :: AccST, U, AccSH :: P, S] =
        new CoSplitLeft0[AccP, AccSH :: AccST, U, AccSH :: P, S] {
          def apply(accP: AccP, accS: AccSH :: AccST): (AccSH :: P) :: S :: HNil =
            slt(accP, accS.tail) match {
              case prefix :: suffix :: HNil => (accS.head :: prefix) :: suffix :: HNil
            }
        }
    }

    object CoSplitLeft0 extends LowPrioritySplitLeft0 {
      implicit def hlistSplitLeft2[P <: HList, SH, SH0 <: SH, ST <: HList]
        : CoSplitLeft0[P, SH0 :: ST, SH, P, SH0 :: ST] =
        new CoSplitLeft0[P, SH0 :: ST, SH, P, SH0 :: ST] {
          def apply(accP: P, accS: SH0 :: ST): P :: (SH0 :: ST) :: HNil = accP :: accS :: HNil
        }
    }
  }

  /**
    * Type Class witnessing that an 'HList' can be spanned with a 'Poly' to produce two 'HList's
    *
    * @author Thijs Broersen
    */
  @implicitNotFound(
    "Implicit not found: lspace.librarian.traversal.Span[${L}, ${U}]. You requested to split at an element of Poly ${U}, but there is none in the HList ${L}.")
  trait Span[L <: HList, U <: Poly] extends DepFn1[L] with Serializable {
    type Prefix <: HList
    type Suffix <: HList
    type Out = (Prefix, Suffix)

    def apply(l: L): Out = toTuple2(product(l))
    def product(l: L): Prefix :: Suffix :: HNil
  }

  object Span {
    import shapeless.poly._

    def apply[L <: HList, U <: Poly](implicit span: Span[L, U]): Aux[L, U, span.Prefix, span.Suffix] = span

    type Aux[L <: HList, U <: Poly, Prefix0 <: HList, Suffix0 <: HList] = Span[L, U] {
      type Prefix = Prefix0
      type Suffix = Suffix0
    }

    implicit def spanLeft[H, L <: HList, U <: Poly, ClrResult, P <: HList, S <: HList](
        implicit
        clr: Case1.Aux[U, H, ClrResult],
        spanLeft: Span0[HNil, H :: L, U, P, S]): Aux[H :: L, U, P, S] =
      new Span[H :: L, U] {
        type Prefix = P
        type Suffix = S

        def product(l: H :: L): Prefix :: Suffix :: HNil = spanLeft(HNil, l)
      }

    trait Span0[AccP <: HList, AccS <: HList, U <: Poly, P <: HList, S <: HList] extends Serializable {
      def apply(accP: AccP, accS: AccS): P :: S :: HNil
    }

    trait LowPrioritySpan0 {
      implicit def hlistSpan1[AccP <: HList, AccSH, AccST <: HList, U <: Poly, P <: HList, S <: HList, ClrResult](
          implicit slt: Span0[AccP, AccST, U, P, S]): Span0[AccP, AccSH :: AccST, U, AccSH :: P, S] =
        new Span0[AccP, AccSH :: AccST, U, AccSH :: P, S] {
          def apply(accP: AccP, accS: AccSH :: AccST): (AccSH :: P) :: S :: HNil =
            slt(accP, accS.tail) match {
              case prefix :: suffix :: HNil => (accS.head :: prefix) :: suffix :: HNil
            }
        }
    }

    object Span0 extends LowPrioritySpan0 {
      implicit def hlistSpan2[P <: HList, U <: Poly, SH, ST <: HList, Result <: HList](
          implicit
          collect: shapeless.ops.hlist.Collect.Aux[SH :: HNil, U, Result],
          ev: Result =:= HNil): Span0[P, SH :: ST, U, P, SH :: ST] =
        new Span0[P, SH :: ST, U, P, SH :: ST] {
          def apply(accP: P, accS: SH :: ST): P :: (SH :: ST) :: HNil = accP :: accS :: HNil
        }

      implicit def hlistSpan0[P <: HList, U <: Poly, ST <: HList](
          implicit
          ev: ST =:= HNil): Span0[P, ST, U, P, ST] =
        new Span0[P, ST, U, P, ST] {
          def apply(accP: P, accS: ST): P :: ST :: HNil = accP :: accS :: HNil
        }
    }
  }

//  object PTest extends Poly1 {
//    implicit def int     = at[Int](identity)
//    implicit def boolean = at[Boolean](identity)
//  }
//
//  def span[T <: HList, P <: HList, S <: HList](t: T)(implicit span: Span.Aux[T, PTest.type, P, S]): (P, S)
//
//  span(1 :: HNil)
//  span("1" :: HNil)
//  span(HNil)
//  span("a" :: 1 :: "b" :: 1 :: HNil)
//  span(2 :: 1 :: true :: "b" :: HNil)
}
