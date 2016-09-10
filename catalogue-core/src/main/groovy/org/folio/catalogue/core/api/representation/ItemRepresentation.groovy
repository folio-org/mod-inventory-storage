package org.folio.catalogue.core.api.representation

import org.folio.catalogue.core.domain.Item
import io.vertx.groovy.core.http.HttpServerRequest
import org.folio.catalogue.core.api.ResourceMap

class ItemRepresentation {
  static toMap(Item item, HttpServerRequest request) {
    def representation = [:]

    representation.title = item.title
    representation.barcode = item.barcode

    representation.links = [
      'self'    : ResourceMap.itemAbsolute("/${item.id}", request),
      'instance': item.instanceLocation
    ]

    representation
  }
}
