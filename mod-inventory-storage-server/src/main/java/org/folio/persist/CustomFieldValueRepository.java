package org.folio.persist;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.persist.PostgresClient;

/**
 * Reads the custom field values stored on records.
 */
public final class CustomFieldValueRepository {

  private final Vertx vertx;

  public CustomFieldValueRepository(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * Counts the records of the table whose value of the field is the option or a list containing it.
   */
  public Future<Integer> countOptionHolders(String tenantId, String table, String refId, String optionId) {
    var sql = "SELECT COUNT(*) FROM " + PostgresClient.convertToPsqlStandard(tenantId) + "." + table
      + " WHERE jsonb->'customFields' @> jsonb_build_object($1::text, $2::text)"
      + " OR jsonb->'customFields' @> jsonb_build_object($1::text, jsonb_build_array($2::text))";
    return PostgresClient.getInstance(vertx, tenantId)
      .selectSingle(sql, Tuple.of(refId, optionId))
      .map(row -> row.getInteger(0));
  }
}
