package org.folio.inventory.org.folio.inventory.api.resources.ingest

import io.vertx.groovy.core.buffer.Buffer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Item
import org.folio.inventory.org.folio.inventory.ingest.ModsParser
import org.folio.metadata.common.api.response.JsonResponse

class ModsIngestion {
  public static void register(Router router) {
    router.post("/ingest/mods" + "*").handler(BodyHandler.create())

    router.post("/ingest/mods").handler({ routingContext ->
      routingContext.fileUploads().each { f ->
        //Definitely shouldn't be blocking for large files
        Buffer uploadedFile = routingContext.vertx().fileSystem().readFileBlocking(f.uploadedFileName());

        Item item = new ModsParser().parseRecord(uploadedFile.toString())

        JsonResponse.success(routingContext.response(), item)
      }
    })

  }
}
