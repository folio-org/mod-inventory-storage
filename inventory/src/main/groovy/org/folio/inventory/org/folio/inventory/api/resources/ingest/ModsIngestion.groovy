package org.folio.inventory.org.folio.inventory.api.resources.ingest

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Item
import org.folio.inventory.org.folio.inventory.ingest.ModsParser
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse

class ModsIngestion {
  public static void register(Router router) {
    router.post(relativeModsIngestPath() + "*").handler(BodyHandler.create())

    router.post(relativeModsIngestPath()).handler({ routingContext ->

      if(routingContext.fileUploads().size() > 1) {
        ClientErrorResponse.badRequest(routingContext.response(),
          "Cannot ingest multiple files in a single request")
        return
      }

      routingContext.fileUploads().each { f ->
        def modsFileBuffer = routingContext.vertx().fileSystem().readFileBlocking(f.uploadedFileName()).toString()

        Item item = new ModsParser().parseRecord(modsFileBuffer.toString())

        JsonResponse.success(routingContext.response(), item)
      }
    })

  }

  private static String relativeModsIngestPath() {
    "/ingest/mods"
  }
}
