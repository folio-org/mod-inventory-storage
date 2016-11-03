package org.folio.inventory.org.folio.inventory.api.resources.ingest

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Item
import org.folio.inventory.org.folio.inventory.ingest.ModsParser
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.ServerErrorResponse

class ModsIngestion {
  public static void register(Router router) {
    router.post(relativeModsIngestPath() + "*").handler(BodyHandler.create())

    router.post(relativeModsIngestPath()).handler({ routingContext ->

      if(routingContext.fileUploads().size() > 1) {
        ClientErrorResponse.badRequest(routingContext.response(),
          "Cannot ingest multiple files in a single request")
        return
      }

      def uploadedFileName = routingContext.fileUploads().toList().first().uploadedFileName()

      routingContext.vertx().fileSystem().readFile(uploadedFileName, { result ->
        if(result.succeeded()) {
          def uploadedFileContents = result.result().toString()

          Item item = new ModsParser().parseRecord(uploadedFileContents)

          JsonResponse.success(routingContext.response(), item)
        }
        else {
          ServerErrorResponse.internalError(routingContext.response(), result.cause().toString())
        }
      })
    })
  }

  private static String relativeModsIngestPath() {
    "/ingest/mods"
  }
}
