package lspace.services.rest.endpoints

import cats.effect.IO
import com.twitter.finagle.http.Request
import io.finch.Endpoint
import lspace.codec.ActiveContext

trait Api extends Endpoint.Module[IO] {
//  def activeContext: ActiveContext
//  def api: Endpoint[IO, _]
//  def compiled: Endpoint.Compiled[IO]
}
