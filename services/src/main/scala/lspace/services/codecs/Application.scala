package lspace.services.codecs

import shapeless.Witness

object Application {
  type JsonLD = Witness.`"application/ld+json"`.T
}