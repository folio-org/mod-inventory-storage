package org.folio.inventory.resources.ingest

import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.file.FileSystem
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.Messages
import org.folio.inventory.parsing.ModsParser
import org.folio.inventory.parsing.UTF8LiteralCharacterEncoding
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.RedirectResponse
import org.folio.metadata.common.api.response.ServerErrorResponse

class ModsIngestion {
  private final IngestJobCollection ingestJobCollection

  ModsIngestion(IngestJobCollection ingestJobCollection) {

    this.ingestJobCollection = ingestJobCollection
  }

  public void register(Router router) {
    router.post(relativeModsIngestPath() + "*").handler(BodyHandler.create())
    router.post(relativeModsIngestPath()).handler(this.&ingest)
    router.get(relativeModsIngestPath() + "/status/:id").handler(this.&status)
  }

  private ingest(RoutingContext routingContext) {
    if(routingContext.fileUploads().size() > 1) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "Cannot parse multiple files in a single request")
      return
    }

    readUploadedFile(routingContext)
  }

  private status(RoutingContext routingContext) {
    ingestJobCollection.findById(routingContext.request().getParam("id"), {
        JsonResponse.success(routingContext.response(),
          ["status" : it.state.toString()])
      })
  }

  private FileSystem readUploadedFile(RoutingContext routingContext) {
    readFile(routingContext)
  }

  private FileSystem readFile(RoutingContext routingContext) {
    routingContext.vertx().fileSystem().readFile(uploadFileName(routingContext),
      { result ->
        if (result.succeeded()) {
          def uploadedFileContents = result.result().toString()

          def records = new ModsParser(new UTF8LiteralCharacterEncoding())
            .parseRecords(uploadedFileContents)

          def convertedRecords = records.collect {
            ["title": "${it.title}", "barcode":"${it.barcode}" ]
          }

          ingestJobCollection.add(new IngestJob(IngestJobState.REQUESTED), {
            routingContext.vertx().eventBus().send(
              Messages.START_INGEST.Address,
              new JsonObject(["records" : convertedRecords]),
              ["headers" : ["jobId" : it.id]])

            RedirectResponse.accepted(routingContext.response(),
              statusLocation(routingContext, it.id))
          })

        } else {
          ServerErrorResponse.internalError(
            routingContext.response(), result.cause().toString())
        }
      })
  }

  private String statusLocation(RoutingContext routingContext, jobId) {

    def scheme = routingContext.request().scheme()
    def host = routingContext.request().host()

    "${scheme}://${host}${relativeModsIngestPath()}/status/${jobId}"
  }

  private String uploadFileName(RoutingContext routingContext) {
    routingContext.fileUploads().toList().first()
      .uploadedFileName()
  }

  private static String relativeModsIngestPath() {
    "/inventory/ingest/mods"
  }
}
