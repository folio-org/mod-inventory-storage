package org.folio.inventory.org.folio.inventory.api.resources.ingest

import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Item
import org.folio.inventory.org.folio.inventory.parsing.ModsParser
import org.folio.inventory.storage.memory.InMemoryItemCollection
import org.folio.metadata.common.CollectAll
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.ServerErrorResponse

class ModsIngestion {
  public void register(Router router) {
    router.post(relativeModsIngestPath() + "*").handler(BodyHandler.create())
    router.post(relativeModsIngestPath()).handler(this.&ingest)
  }

  private ingest(RoutingContext routingContext) {
    if(routingContext.fileUploads().size() > 1) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "Cannot parsing multiple files in a single request")
      return
    }

    def uploadedFileName = routingContext.fileUploads().toList().first()
      .uploadedFileName()

    routingContext.vertx().fileSystem().readFile(uploadedFileName,
      { result ->
        if(result.succeeded()) {
          def uploadedFileContents = result.result().toString()

          def storage = new InMemoryItemCollection()

          def records = new ModsParser().parseRecords(uploadedFileContents)

          def collectAll = new CollectAll<Item>()

          records.stream()
            .map({ new Item(UUID.randomUUID().toString(),
            it.title,
            it.barcode)
          })
            .forEach({ storage.add(it, collectAll.receive()) })

          collectAll.collect(
            { JsonResponse.success(routingContext.response(), it) })
        }
        else {
          ServerErrorResponse.internalError(
            routingContext.response(), result.cause().toString())
        }
      })
  }

  private static String relativeModsIngestPath() {
    "/parsing/mods"
  }
}
