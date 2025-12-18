package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.internal.ArrayTuple;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InventoryHierarchyInstanceIds;
import org.folio.rest.jaxrs.resource.InventoryHierarchy;

public class InventoryHierarchyApi extends AbstractInstanceRecordsApi implements InventoryHierarchy {

  private static final String SQL_UPDATED_INSTANCES_IDS =
    "select * from get_updated_instance_ids_view($1,$2,$3,$4,$5,$6);";
  private static final String SQL_INSTANCES = "select * from get_items_and_holdings_view($1,$2);";
  private static final String SUPPRESSED_TRUE_FILTER = "(instance.jsonb ->> 'discoverySuppress')::bool = false";
  private static final String SQL_INITIAL_LOAD =
    "SELECT id as \"instanceId\",\n"
    + "       instance.jsonb ->> 'source' AS source,\n"
    + "       strToTimestamp(instance.jsonb -> 'metadata' ->> 'updatedDate') AS \"updatedDate\",\n"
    + "       (instance.jsonb ->> 'discoverySuppress')::bool AS \"suppressFromDiscovery\",\n"
    + "       false AS deleted\n"
    + "FROM instance\n"
    + "WHERE (CAST($1 as varchar) IS NULL OR (instance.jsonb ->> 'source')::varchar = $1)";
  private static final String SQL_INITIAL_LOAD_DELETED_RECORDS_SUPPORT_PART =
    " UNION ALL\n"
    + "\t(SELECT (jsonb #>> '{record,id}')::uuid            AS \"instanceId\",\n"
    + "        jsonb #>> '{record,source}'                 AS source,\n"
    + "        strToTimestamp(jsonb ->> 'createdDate')     AS \"updatedDate\",\n"
    + "        false                                       AS \"suppressFromDiscovery\",\n"
    + "        true                                        AS deleted\n"
    + "\tFROM audit_instance"
    + "\tWHERE (CAST($1 as varchar) IS NULL OR (jsonb ->> 'source')::varchar = $1))";

  @Validate
  @Override
  public void getInventoryHierarchyUpdatedInstanceIds(String startDate, String endDate, boolean deletedRecordSupport,
                                                      boolean skipSuppressedFromDiscoveryRecords,
                                                      boolean onlyInstanceUpdateDate, String source,
                                                      RoutingContext routingContext, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    if (StringUtils.isEmpty(startDate) && StringUtils.isEmpty(endDate)) {
      handleInitialLoad(deletedRecordSupport, skipSuppressedFromDiscoveryRecords, source,
        routingContext, okapiHeaders, asyncResultHandler, vertxContext);
    } else {
      handleUpdatedInstances(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords,
        onlyInstanceUpdateDate, source, routingContext, okapiHeaders, asyncResultHandler, vertxContext);
    }
  }

  private void handleInitialLoad(boolean deletedRecordSupport, boolean skipSuppressedFromDiscoveryRecords,
                                  String source, RoutingContext routingContext, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String sql = buildInitialLoadSql(skipSuppressedFromDiscoveryRecords, deletedRecordSupport);
    Tuple tuple = new ArrayTuple(1).addValue(source);
    fetchRecordsByQuery(sql, () -> tuple, routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private void handleUpdatedInstances(String startDate, String endDate, boolean deletedRecordSupport,
                                       boolean skipSuppressedFromDiscoveryRecords, boolean onlyInstanceUpdateDate,
                                       String source, RoutingContext routingContext, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    fetchRecordsByQuery(SQL_UPDATED_INSTANCES_IDS,
      () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords,
        tuple -> {
          tuple.addBoolean(onlyInstanceUpdateDate);
          if (Objects.nonNull(source)) {
            tuple.addString(source);
          } else {
            tuple.addValue(null);
          }
        }),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private String buildInitialLoadSql(boolean skipSuppressedFromDiscoveryRecords, boolean deletedRecordSupport) {
    String sql = SQL_INITIAL_LOAD;
    if (skipSuppressedFromDiscoveryRecords) {
      sql += " AND " + SUPPRESSED_TRUE_FILTER;
    }
    if (deletedRecordSupport) {
      sql += SQL_INITIAL_LOAD_DELETED_RECORDS_SUPPORT_PART;
    }
    return sql;
  }

  @Validate
  @Override
  public void postInventoryHierarchyItemsAndHoldings(InventoryHierarchyInstanceIds entity,
                                                     RoutingContext routingContext,
                                                     Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {

    UUID[] ids = entity.getInstanceIds().stream().map(UUID::fromString).toArray(UUID[]::new);

    fetchRecordsByQuery(SQL_INSTANCES,
      () -> createPostgresParams(ids, entity.getSkipSuppressedFromDiscoveryRecords()),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext
    );
  }
}
