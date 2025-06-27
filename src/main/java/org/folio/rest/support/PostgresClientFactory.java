package org.folio.rest.support;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

public final class PostgresClientFactory {

  private PostgresClientFactory() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  /**
   * Return a PostgresClient.
   *
   * @param vertxContext Where to get a Vertx from.
   * @param okapiHeaders Where to get the tenantId from.
   * @return the PostgresClient for the vertx and the tenantId
   */
  public static PostgresClient getInstance(Context vertxContext, Map<String, String> okapiHeaders) {
    return getInstance(vertxContext, TenantTool.tenantId(okapiHeaders));
  }

  /**
   * Return a PostgresClient.
   *
   * @param vertxContext Where to get a Vertx from.
   * @param tenantId     A tenantId.
   * @return the PostgresClient for the vertx and the tenantId
   */
  public static PostgresClient getInstance(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId);
  }
}
