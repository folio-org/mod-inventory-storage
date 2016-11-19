package org.folio.inventory.org.folio.inventory.api.resources.ingest

import io.vertx.groovy.core.file.FileSystem
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Item
import org.folio.inventory.org.folio.inventory.parsing.ModsParser
import org.folio.inventory.storage.memory.InMemoryItemCollection
import org.folio.metadata.common.CollectAll
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.RedirectResponse
import org.folio.metadata.common.api.response.ServerErrorResponse

class ModsIngestion {
  private InMemoryItemCollection itemCollection = new InMemoryItemCollection()

  public void register(Router router) {
    router.post(relativeModsIngestPath() + "*").handler(BodyHandler.create())
    router.post(relativeModsIngestPath()).handler(this.&ingest)
    router.get("/ingest/mods/status").handler(this.&status)
  }

  private ingest(RoutingContext routingContext) {
    if(routingContext.fileUploads().size() > 1) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "Cannot parsing multiple files in a single request")
      return
    }

    readUploadedFile(routingContext)
  }

  private status(RoutingContext routingContext) {
    itemCollection.findAll {
      JsonResponse.success(routingContext.response(),
        ["status":"completed", "items": it ])
    }
  }

  private FileSystem readUploadedFile(RoutingContext routingContext) {
    readFile(routingContext)
  }

  private FileSystem readFile(RoutingContext routingContext) {
    routingContext.vertx().fileSystem().readFile(uploadFileName(routingContext),
      { result ->
        if (result.succeeded()) {
          def uploadedFileContents = result.result().toString()


          def records = new ModsParser().parseRecords(uploadedFileContents)

          def collectAll = new CollectAll<Item>()

          records.stream()
            .map({
            new Item(UUID.randomUUID().toString(),
              it.title,
              it.barcode)
          }).forEach({ itemCollection.add(it, collectAll.receive()) })

          RedirectResponse.accepted(routingContext.response(),
            statusLocation(routingContext))

        } else {
          ServerErrorResponse.internalError(
            routingContext.response(), result.cause().toString())
        }
      })
  }

  private String statusLocation(RoutingContext routingContext) {

    def scheme = routingContext.request().scheme()
    def currentUri = routingContext.request().uri()
    def host = routingContext.request().host()

    "${scheme}://${host}${currentUri}/status"
  }

  private String uploadFileName(RoutingContext routingContext) {
    routingContext.fileUploads().toList().first()
      .uploadedFileName()
  }

  private static String relativeModsIngestPath() {
    "/ingest/mods"
  }
}
