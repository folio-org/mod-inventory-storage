package org.folio.services.consortium;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import java.util.Map;
import org.folio.services.caches.ConsortiumData;

public record SynchronizationContext(
  ConsortiumData consortiaData,
  Map<String, String> headers,
  Vertx vertx,
  HttpClient httpClient
) {

  public String tenantId() {
    return headers.get(TENANT);
  }
}
