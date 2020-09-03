package org.folio.rest.impl;

import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InventoryInstanceIds;
import org.folio.rest.jaxrs.resource.InventoryHierarchy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class InventoryHierarchyAPI extends AbstractInstanceRecordsAPI implements InventoryHierarchy {

  private static final String SQL_UPDATED_INSTANCES_IDS = "select * from get_updated_instance_ids_view($1,$2,$3,$4,$5);";
  private static final String SQL_INSTANCES = "select * from get_items_and_holdings_view($1,$2);";

  @Validate
  @Override
  public void getInventoryHierarchyUpdatedInstanceIds(String startDate, String endDate, boolean deletedRecordSupport, boolean skipSuppressedFromDiscoveryRecords,
      boolean onlyInstanceUpdateDate, @Pattern(regexp = "[a-zA-Z]{2}") String lang, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    fetchRecordsByQuery(SQL_UPDATED_INSTANCES_IDS,
      () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords, tuple -> {
        tuple.addBoolean(onlyInstanceUpdateDate);
      }),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext,
      "Get updated instances completed successfully");
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
