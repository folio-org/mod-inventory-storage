package org.folio.rest.impl;

import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InventoryInstanceIds;
import org.folio.rest.jaxrs.resource.InventoryHierarchy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.impl.ArrayTuple;

public class InventoryHierarchyAPI extends AbstractInstanceRecordsAPI implements InventoryHierarchy {

  private static final String SQL_UPDATED_INSTANCES_IDS = "select * from get_updated_instance_ids_view($1,$2,$3,$4,$5);";
  private static final String SQL_INSTANCES = "select * from get_items_and_holdings_view($1,$2);";
  private static final String SUPPRESSED_TRUE_FILTER = " WHERE (instance.jsonb ->> 'discoverySuppress')::bool = false";
  private static final String SQL_INITIAL_LOAD = "SELECT id as \"instanceId\",\n" +
    "       instance.jsonb ->> 'source' AS source,\n" +
    "       strToTimestamp(instance.jsonb -> 'metadata' ->> 'updatedDate') AS \"updatedDate\",\n" +
    "       (instance.jsonb ->> 'discoverySuppress')::bool AS \"suppressFromDiscovery\",\n" +
    "       false AS deleted\n" +
    "FROM instance";
  private static final String SQL_INITIAL_LOAD_DELETED_RECORDS_SUPPORT_PART = " UNION ALL\n" +
    "\tSELECT (jsonb #>> '{record,id}')::uuid            AS \"instanceId\",\n" +
    "        jsonb #>> '{record,source}'                 AS source,\n" +
    "        strToTimestamp(jsonb ->> 'createdDate')     AS \"updatedDate\",\n" +
    "        false                                       AS \"suppressFromDiscovery\",\n" +
    "        true                                        AS deleted\n" +
    "\tFROM audit_instance";

  @Validate
  @Override
  public void getInventoryHierarchyUpdatedInstanceIds(String startDate, String endDate, boolean deletedRecordSupport, boolean skipSuppressedFromDiscoveryRecords,
      boolean onlyInstanceUpdateDate, @Pattern(regexp = "[a-zA-Z]{2}") String lang, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if(StringUtils.isEmpty(startDate) && StringUtils.isEmpty(endDate)) {
      String sql = SQL_INITIAL_LOAD;
      if(skipSuppressedFromDiscoveryRecords) {
        sql+=SUPPRESSED_TRUE_FILTER;
      }
      if(deletedRecordSupport) {
        sql+=SQL_INITIAL_LOAD_DELETED_RECORDS_SUPPORT_PART;
      }
      fetchRecordsByQuery(sql, () -> new ArrayTuple(0),
        routingContext, okapiHeaders, asyncResultHandler, vertxContext,
        "Get updated instances completed successfully");
    } else {
      fetchRecordsByQuery(SQL_UPDATED_INSTANCES_IDS,
        () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords, tuple -> {
          tuple.addBoolean(onlyInstanceUpdateDate);
        }),
        routingContext, okapiHeaders, asyncResultHandler, vertxContext,
        "Get updated instances completed successfully");
    }
  }

  @Validate
  @Override
  public void postInventoryHierarchyItemsAndHoldings(InventoryInstanceIds entity, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    UUID[] ids = entity.getInstanceIds().stream().map(UUID::fromString).toArray(UUID[]::new);

    fetchRecordsByQuery(SQL_INSTANCES,
      () -> createPostgresParams(ids, entity.getSkipSuppressedFromDiscoveryRecords()),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext,
      "Select from oai pmh instances view completed successfully");
  }

}
