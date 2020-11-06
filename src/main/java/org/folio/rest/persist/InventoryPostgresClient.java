package org.folio.rest.persist;

import io.vertx.core.AsyncResult;

public final class InventoryPostgresClient {
  public void resolveConnection(AsyncResult<SQLConnection> connection) {
    if (connection.failed()) {
      return;
    }

    connection.result().conn.cancelRequest(p -> {});
  }
}
