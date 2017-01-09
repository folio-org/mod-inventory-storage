package org.folio.inventory.resources.ingest

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

class IngestRecordConverter {
  def toJson(records) {
    records.collect {
      def convertedIdentifiers = it.identifiers.collect {
        ["namespace": "${it.namespace}", "value": "${it.value}"]
      }

      new JsonObject()
        .put("title", it.title)
        .put("barcode", it.barcode)
        .put("identifiers", new JsonArray(convertedIdentifiers))
    }
  }
}
