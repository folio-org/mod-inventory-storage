package org.folio.rest.impl;

import static org.folio.rest.impl.StorageHelper.getCql;
import static org.folio.rest.impl.StorageHelper.logAndSaveError;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

public class LocationApi implements org.folio.rest.jaxrs.resource.Locations {
  public static final String LOCATION_TABLE = "location";
  public static final String URL_PREFIX = "/locations";
  public static final String SERVICEPOINT_IDS = "servicePointIds";
  public static final String PRIMARY_SERVICEPOINT = "primaryServicePoint";
  private static final Logger logger = LogManager.getLogger();

  // Note, this is the way to get rid of unnecessary try-catch blocks. Use the
  // same everywhere!
  @Validate
  @Override
  public void getLocations(
    String query,
    int offset,
    int limit,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = tenantId(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = getCql(query, limit, offset, LOCATION_TABLE);
    } catch (FieldException e) {
      String message = logAndSaveError(e);
      logger.warn("XXX - Query exception ", e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationsResponse.respond400WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LOCATION_TABLE, Location.class,
        new String[] {"*"}, cql, true, true, reply -> {
          // netbeans, please indent here!
          if (reply.failed()) {
            String message = logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsResponse.respond400WithTextPlain(message)));
          } else {
            Locations shelfLocations = new Locations();
            List<Location> shelfLocationsList = reply.result().getResults();
            shelfLocations.setLocations(shelfLocationsList);
            shelfLocations.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsResponse.respond200WithApplicationJson(shelfLocations)));
          }
        });
  }

  @Validate
  @Override
  public void postLocations(
    String lang,
    Location entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = tenantId(okapiHeaders);

    runLocationChecks(checkIdProvided(entity), checkAtLeastOneServicePoint(entity),
      checkPrimaryServicePointRelationship(entity))
      .onComplete(checksResult -> {
        if (checksResult.succeeded()) {

          PostgresClient.getInstance(vertxContext.owner(), tenantId).save(LOCATION_TABLE, entity.getId(), entity,
            reply -> {
              if (reply.failed()) {
                String message = logAndSaveError(reply.cause());
                if (message != null && message.contains("duplicate key value violates unique constraint")) {
                  asyncResultHandler.handle(
                    Future.succeededFuture(PostLocationsResponse.respond422WithApplicationJson(ValidationHelper
                      .createValidationErrorMessage("shelflocation", entity.getId(), "Location already exists"))));
                } else {
                  asyncResultHandler
                    .handle(Future.succeededFuture(PostLocationsResponse.respond500WithTextPlain(message)));
                }
              } else {
                String responseObject = reply.result();
                entity.setId(responseObject);
                asyncResultHandler
                  .handle(Future.succeededFuture(PostLocationsResponse.respond201WithApplicationJson(entity,
                    PostLocationsResponse.headersFor201().withLocation(URL_PREFIX + responseObject))));
              }
            });

        } else {
          String message = logAndSaveError(checksResult.cause());
          asyncResultHandler.handle(
            Future.succeededFuture(PostLocationsResponse.respond422WithApplicationJson(ValidationHelper
              .createValidationErrorMessage(((LocationCheckError) checksResult.cause()).getField(),
                entity.getId(), message))));
        }

      });

  }

  @Validate
  @Override
  public void deleteLocations(String lang,
                              Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(), TenantTool.calculateTenantId(tenantId))
      .execute(String.format("DELETE FROM %s_%s.%s",
          tenantId, "mod_inventory_storage", LOCATION_TABLE),
        reply -> {
          if (reply.succeeded()) {
            asyncResultHandler.handle(Future.succeededFuture(DeleteLocationsResponse.respond204()));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteLocationsResponse.respond500WithTextPlain(reply.cause().getMessage())));
          }
        });
  }

  @Validate
  @Override
  public void getLocationsById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(LOCATION_TABLE, Location.class, id, okapiHeaders, vertxContext,
      GetLocationsByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putLocationsById(
    String id,
    String lang,
    Location entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    runLocationChecks(checkIdChange(id, entity), checkAtLeastOneServicePoint(entity),
      checkPrimaryServicePointRelationship(entity), checkForDuplicateServicePoints(entity))
      .onComplete(checksResult -> {
        if (checksResult.failed()) {
          String message = logAndSaveError(checksResult.cause());
          asyncResultHandler
            .handle(Future.succeededFuture(PutLocationsByIdResponse.respond422WithApplicationJson(
              ValidationHelper.createValidationErrorMessage(
                ((LocationCheckError) checksResult.cause()).getField(), entity.getId(), message))));
          return;
        }
        PgUtil.put(LOCATION_TABLE, entity, id, okapiHeaders, vertxContext,
          PutLocationsByIdResponse.class, asyncResultHandler);
      });
  }

  @Validate
  @Override
  public void deleteLocationsById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(LOCATION_TABLE, id, okapiHeaders, vertxContext,
      DeleteLocationsByIdResponse.class, asyncResultHandler);
  }

  @SafeVarargs
  private CompositeFuture runLocationChecks(Future<LocationCheckError>... futures) {
    List<Future<LocationCheckError>> allFutures = new ArrayList<>(Arrays.asList(futures));
    return Future.all(allFutures);
  }

  private Future<LocationCheckError> checkIdProvided(Location entity) {

    Future<LocationCheckError> future = Future.succeededFuture();

    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }

    return future;

  }

  private Future<LocationCheckError> checkIdChange(String id, Location entity) {

    Future<LocationCheckError> future = Future.succeededFuture();

    if (!id.equals(entity.getId())) {
      future = Future.failedFuture(new LocationCheckError("id", "Illegal operation: id cannot be changed"));
    }

    return future;

  }

  private Future<LocationCheckError> checkAtLeastOneServicePoint(Location entity) {

    Future<LocationCheckError> future = Future.succeededFuture();

    if (entity.getServicePointIds().isEmpty()) {
      future = Future.failedFuture(
        new LocationCheckError(SERVICEPOINT_IDS, "A location must have at least one Service Point assigned."));
    }

    return future;

  }

  private Future<LocationCheckError> checkPrimaryServicePointRelationship(Location entity) {

    Future<LocationCheckError> future = Future.succeededFuture();

    if (!entity.getServicePointIds().contains(entity.getPrimaryServicePoint())) {
      future = Future
        .failedFuture(new LocationCheckError(PRIMARY_SERVICEPOINT,
          "A Location's Primary Service point must be included as a Service Point."));
    }

    return future;

  }

  private Future<LocationCheckError> checkForDuplicateServicePoints(Location entity) {

    Future<LocationCheckError> future = Future.succeededFuture();

    Set<String> set = new HashSet<>();
    Set<String> duplicateElements = new HashSet<>();

    for (String element : entity.getServicePointIds()) {
      if (!set.add(element)) {
        duplicateElements.add(element);
      }
    }

    if (!duplicateElements.isEmpty()) {
      future = Future.failedFuture(
        new LocationCheckError(SERVICEPOINT_IDS, "A Service Point can only appear once on a Location."));
    }

    return future;

  }

  private static class LocationCheckError extends Exception {

    private final String field;

    LocationCheckError(String field, String message) {
      super(message);
      this.field = field;
    }

    public String getField() {
      return field;
    }

  }

}
