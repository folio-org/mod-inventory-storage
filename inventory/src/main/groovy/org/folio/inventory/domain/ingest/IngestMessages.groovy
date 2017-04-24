package org.folio.inventory.domain.ingest

import io.vertx.core.json.JsonObject
import org.folio.inventory.domain.Messages
import org.folio.metadata.common.Context
import org.folio.metadata.common.messaging.JsonMessage

class IngestMessages {
  static start(records, Map materialTypes, jobId, Context context) {
    new JsonMessage(Messages.START_INGEST.Address,
    headers(jobId, context),
    new JsonObject()
      .put("records", records)
      .put("materialTypes", materialTypes))
  }

  static completed(jobId, Context context) {
    new JsonMessage(Messages.INGEST_COMPLETED.Address,
      headers(jobId, context),
      new JsonObject())
  }

  private static Map<String, String> headers(jobId, Context context) {
    ["jobId"        : jobId,
     "tenantId"     : context.tenantId,
     "token"     : context.token,
     "okapiLocation": context.okapiLocation]
  }

}
