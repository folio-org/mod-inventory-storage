package org.folio.inventory.resources.ingest

import io.vertx.groovy.core.file.FileSystem
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.ingest.IngestMessages
import org.folio.inventory.parsing.ModsParser
import org.folio.inventory.parsing.UTF8LiteralCharacterEncoding
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.response.*
import org.folio.metadata.common.domain.Success

class ModsIngestion {
  private final Storage storage

  ModsIngestion(final Storage storage) {
    this.storage = storage
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

    def context = new WebContext(routingContext)

    storage.getIngestJobCollection(context)
      .findById(routingContext.request().getParam("id"),
      { Success it ->
        JsonResponse.success(routingContext.response(),
          ["status" : it.result.state.toString()])
      }, FailureResponseConsumer.serverError(routingContext.response()))
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

          def convertedRecords = new IngestRecordConverter().toJson(records)

          def context = new WebContext(routingContext)

          storage.getIngestJobCollection(context)
            .add(new IngestJob(IngestJobState.REQUESTED), { job ->

            IngestMessages.start(convertedRecords, job.id, context)
              .send(routingContext.vertx())

            RedirectResponse.accepted(routingContext.response(),
              statusLocation(routingContext, job.id))
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
