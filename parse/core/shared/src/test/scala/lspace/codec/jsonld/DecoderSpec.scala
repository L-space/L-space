package lspace.codec.jsonld

import lspace._
import lspace.Label.D._
import lspace.Label.P._
import lspace.codec.{ActiveContext, ActiveProperty, NamedActiveContext}
import lspace.provider.mem.MemGraph
import lspace.structure.SampledGraph
import org.scalatest.{AsyncWordSpec, FutureOutcome, Matchers}
import scribe.Level
import scribe.format.Formatter

import scala.collection.immutable.ListMap
import scala.concurrent.duration._

abstract class DecoderSpec(decoder: Decoder) extends AsyncWordSpec with Matchers {

  import decoder._
  import lspace.Implicits.Scheduler.global
  override def executionContext = lspace.Implicits.Scheduler.global

  scribe.Logger.root
    .clearHandlers()
    .clearModifiers()
    .withHandler(minimumLevel = Some(Level.Trace), formatter = Formatter.enhanced)
    .replace()

  val graph: Graph = decoder.graph

  "The Decoder" should {
    "decode any ontology" which {
      "is served by schema.org" in {
        decoder
          .toOntology("https://schema.org/Person")(ActiveContext())
          .map { ontology =>
            ontology.iri shouldBe "https://schema.org/Person"
            ontology
              .properties("https://schema.org/additionalName")
              .exists(_.range(`@string`.iri).isDefined) shouldBe true
            ontology.properties("https://schema.org/colleagues").isDefined shouldBe true
          }
          .timeout(15.seconds)
          .runToFuture
      }
    }
    "decode any node" which {
      "uses a context if provided" in {
        Ontology.ontologies.getOrCreate("https://example.org/Person")
        val defaultContext = ActiveContext(
          `@prefix` = ListMap("naam" -> "name"),
          definitions = Map("name"   -> ActiveProperty(`@type` = `@string` :: Nil, property = Property("name")))
        )
        (for {
          node <- decoder.stringToNode(
            """{"@context":{"naam":{"@id":"name","@type":"@string"},"1":"https://example.org/"},"@id":"DecoderSpec-sample/person/567","@ids":"DecoderSpec-sample/person/567","@type":"1:Person","rate":{"@value":4,"@type":"@int"},"1:birthDate":{"@value":"2002-06-13","@type":"@date"},"1:birthPlace":{"@id":"DecoderSpec-sample/place/123"},"balance":{"@value":300,"@type":"@int"},"1:knows":[{"@id":"DecoderSpec-sample/person/56789"},{"@id":"DecoderSpec-sample/person/34567"}],"naam":"Stan"}""")(
            defaultContext)
          _ = node.out(Property("name")).head shouldBe "Stan"
        } yield succeed).runToFuture
      }
    }
    "decode a Context" which {
      "exists of just a remote context" in {
        val defaultContext =
          ActiveContext(remotes = List(NamedActiveContext("https://remote.example.org", ActiveContext())))
        (for {
          json          <- decoder.parse(""""https://remote.example.org"""")
          activeContext <- decoder.contextProcessing.apply(ActiveContext(), json)
        } yield activeContext shouldBe defaultContext).runToFuture
      }
      "exists of a remote context and a local context" in {
        val defaultContext =
          ActiveContext(`@prefix` = ListMap("name" -> "https://example.com/name"),
                        remotes = List(NamedActiveContext("https://remote.example.org", ActiveContext())))

        (for {
          json          <- decoder.parse("""["https://remote.example.org",{"name":"https://example.com/name"}]""")
          activeContext <- decoder.contextProcessing.apply(ActiveContext(), json)
        } yield activeContext shouldBe defaultContext).runToFuture
      }
      "exists of two remote contexts and a local context" in {
        val defaultContext =
          ActiveContext(
            `@prefix` = ListMap("name" -> "https://example.com/name"),
            remotes = List(NamedActiveContext("https://remote.example.org", ActiveContext()),
                           NamedActiveContext("https://remote2.example.org", ActiveContext()))
          )

        (for {
          json <- decoder.parse(
            """["https://remote.example.org", "https://remote2.example.org",{"name":"https://example.com/name"}]""")
          activeContext <- decoder.contextProcessing.apply(ActiveContext(), json)
        } yield activeContext shouldBe defaultContext).runToFuture
      }
    }
  }
}
