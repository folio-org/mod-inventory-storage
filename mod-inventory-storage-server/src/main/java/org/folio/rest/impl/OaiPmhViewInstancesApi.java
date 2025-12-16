package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.OaipmhInstanceIds;
import org.folio.rest.jaxrs.resource.OaiPmhView;

public class OaiPmhViewInstancesApi extends AbstractInstanceRecordsApi implements OaiPmhView {

  private static final String SQL = "select * from pmh_view_function($1,$2,$3,$4);";
  private static final String SQL_UPDATED_INSTANCES_IDS = "select * from pmh_get_updated_instances_ids($1,$2,$3,$4);";
  private static final String SQL_INSTANCES = "select * from pmh_instance_view_function($1,$2);";

  @Validate
  @Override
  public void getOaiPmhViewInstances(String startDate, String endDate, boolean deletedRecordSupport,
                                     boolean skipSuppressedFromDiscoveryRecords,
                                     RoutingContext routingContext, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    fetchRecordsByQuery(SQL,
      () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext
    );
  }

  @Validate
  @Override
  public void getOaiPmhViewUpdatedInstanceIds(String startDate, String endDate, boolean deletedRecordSupport,
                                              boolean skipSuppressedFromDiscoveryRecords,
                                              RoutingContext routingContext,
                                              Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    fetchRecordsByQuery(SQL_UPDATED_INSTANCES_IDS,
      () -> createPostgresParams(startDate, endDate, deletedRecordSupport, skipSuppressedFromDiscoveryRecords),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext
    );
  }

  @Validate
  @Override
  public void postOaiPmhViewEnrichedInstances(OaipmhInstanceIds entity, RoutingContext routingContext,
                                              Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    UUID[] ids = entity.getInstanceIds().stream().map(UUID::fromString).toArray(UUID[]::new);

    fetchRecordsByQuery(SQL_INSTANCES,
      () -> createPostgresParams(ids, entity.getSkipSuppressedFromDiscoveryRecords()),
      routingContext, okapiHeaders, asyncResultHandler, vertxContext
    );
  }
}
