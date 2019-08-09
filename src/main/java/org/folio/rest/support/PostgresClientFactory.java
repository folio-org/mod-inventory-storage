package org.folio.rest.support;

import java.util.Map;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;

public class PostgresClientFactory {
  /**
   * Return a PostgresClient.
   *
   * @param vertxContext Where to get a Vertx from.
   * @param okapiHeaders Where to get the tenantId from.
   * @return the PostgresClient for the vertx and the tenantId
   */
  public PostgresClient getInstance(Context vertxContext, Map<String, String> okapiHeaders) {
    return PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
  }

  /**
   * Return a PostgresClient.
   *
   * @param vertxContext Where to get a Vertx from.
   * @param tenantId     A tenantId.
   * @return the PostgresClient for the vertx and the tenantId
   */
  public PostgresClient getInstance(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId);
  }
}
