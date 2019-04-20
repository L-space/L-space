package lspace.services.codecs

import cats.effect.Effect
import com.twitter.io.Buf
import io.finch.{EncodeStream, StreamInstances, Text}
import lspace.codec.{ActiveContext, ContextedT}
import lspace.encode.{EncodeJson, EncodeJsonLD, EncodeText}
import lspace.librarian.traversal.Collection
import lspace.services.codecs
import lspace.structure.ClassType
import shapeless.=:!=

object Encode {
  type JsonLD[A] = io.finch.Encode.Aux[A, Application.JsonLD]
  type Json[A]   = io.finch.Encode.Aux[A, io.finch.Application.Json]
  type Text[A]   = io.finch.Encode.Aux[A, Text.Plain]

  implicit def encodeArgonautText[A](implicit e: EncodeText[A], activeContext: ActiveContext): Text[A] = {
    io.finch.Encode
      .instance[A, Text.Plain]((a, cs) => Buf.ByteArray.Owned(e.encode(activeContext)(a).getBytes(cs.name)))
  }

  implicit def encodeArgonautJson[A](implicit e: EncodeJson[A], activeContext: ActiveContext): Json[A] = {
    io.finch.Encode
      .instance[A, io.finch.Application.Json]((a, cs) =>
        Buf.ByteArray.Owned(e.encode(activeContext)(a).getBytes(cs.name)))
  }

  implicit def encodeArgonautJsonLD[A](implicit e: EncodeJsonLD[A], activeContext: ActiveContext): JsonLD[A] = {
    io.finch.Encode.instance[A, Application.JsonLD]((a, cs) =>
      Buf.ByteArray.Owned(e.encode(activeContext)(a).getBytes(cs.name)))
  }

  object streamEncoders extends StreamInstances {
    implicit def encodeJsonLDFs2Stream[F[_]: Effect](
        implicit
        A: JsonLD[Collection[Any, ClassType[Any]]])
      : EncodeStream.Aux[F, _root_.fs2.Stream, Collection[Any, ClassType[Any]], codecs.Application.JsonLD] =
      new EncodeNewLineDelimitedFs2Stream[F, Collection[Any, ClassType[Any]], codecs.Application.JsonLD]
  }

  implicit def encodeJsonLDFs2Stream[F[_]: Effect](
      implicit
      A: JsonLD[Collection[Any, ClassType[Any]]])
    : EncodeStream.Aux[F, _root_.fs2.Stream, Collection[Any, ClassType[Any]], codecs.Application.JsonLD] =
    streamEncoders.encodeJsonLDFs2Stream[F]

}
