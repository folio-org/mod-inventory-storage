package org.folio.rest.impl;

import static org.folio.rest.impl.StorageHelper.getCQL;
import static org.folio.rest.impl.StorageHelper.getTenant;
import static org.folio.rest.impl.StorageHelper.idCriterion;
import static org.folio.rest.impl.StorageHelper.isInUse;
import static org.folio.rest.impl.StorageHelper.logAndSaveError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 *
 * @author kurt
 */
public class LocationAPI implements org.folio.rest.jaxrs.resource.Locations {
  private final Messages messages = Messages.getInstance();
  public static final String LOCATION_TABLE = "location";
  private final Logger logger = LoggerFactory.getLogger(LocationAPI.class);
  public static final String URL_PREFIX = "/locations";
  public static final String LOCATION_SCHEMA_PATH = "apidocs/raml/location.json";
  public static final String ID_FIELD_NAME = "'id'";

	private String getErrorResponse(String response) {
		// Check to see if we're suppressing messages or not
		return response;
	}

  @Override
  public void deleteLocations(String lang,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler,
          Context vertxContext)
  {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    PostgresClient.getInstance(vertxContext.owner(), TenantTool.calculateTenantId(tenantId))
      .mutate(String.format("DELETE FROM %s_%s.%s",
        tenantId, "mod_inventory_storage", LOCATION_TABLE),
      reply -> {
        if (reply.succeeded()) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationsResponse.noContent().build()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationsResponse.
              respond500WithTextPlain(reply.cause().getMessage())));
        }
      });
  }

  // Note, this is the way to get rid of unnecessary try-catch blocks. Use the
  // same everywhere!
  @Override
  public void getLocations(
    String query,
    int offset,
    int limit,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    CQLWrapper cql;
    try {
      cql = getCQL(query, limit, offset, LOCATION_TABLE);
    } catch (FieldException e) {
      String message = logAndSaveError(e);
      logger.warn("XXX - Query exception ", e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetLocationsResponse.respond400WithTextPlain(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LOCATION_TABLE, Location.class,
        new String[]{"*"}, cql, true, true, reply -> {
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

  @Override
  public void postLocations(
    String lang,
    Location entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);

		runLocationChecks(checkIdProvided(entity), checkAtLeastOneServicePoint(entity),
				checkPrimaryServicePointRelationship(entity)).setHandler(checksResult -> {

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
						Future.succeededFuture(PostLocationsResponse.respond400WithTextPlain(getErrorResponse(message))));
			}

		});


  }

  @Override
  public void getLocationsById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion = idCriterion(id, LOCATION_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
      LOCATION_TABLE, Location.class, criterion, true, false, getReply -> {
        if (getReply.failed()) {
          String message = logAndSaveError(getReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
            GetLocationsByIdResponse.respond500WithTextPlain(message)));
        } else {
          List<Location> locationList = (List<Location>) getReply.result().getResults();
          if (locationList.isEmpty()) {
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsByIdResponse.respond404WithTextPlain(
                messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
          } else if (locationList.size() > 1) {
            String message = "Multiple locations found with the same id";
            logger.error(message);
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsByIdResponse.respond500WithTextPlain(message)));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(GetLocationsByIdResponse.respond200WithApplicationJson(locationList.get(0))));
          }
        }
      });
  }

  @Override
  public void deleteLocationsById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    String tenantId = getTenant(okapiHeaders);
    Criterion criterion = idCriterion(id, LOCATION_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .delete(LOCATION_TABLE, criterion, deleteReply -> {
      if (deleteReply.failed()) {
        logAndSaveError(deleteReply.cause());
        if (isInUse(deleteReply.cause().getMessage())) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationsByIdResponse
              .respond400WithTextPlain("Location is in use, can not be deleted")));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteLocationsByIdResponse.respond404WithTextPlain("Not found")));
        }
      } else {
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteLocationsByIdResponse.respond204()));
      }
      });
  }

  @Override
  public void putLocationsById(
    String id,
    String lang,
    Location entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {
		runLocationChecks(checkIdChange(id, entity), checkAtLeastOneServicePoint(entity),
				checkPrimaryServicePointRelationship(entity)).setHandler(checksResult -> {
			if (checksResult.succeeded()) {
				String tenantId = getTenant(okapiHeaders);
				Criterion criterion = idCriterion(id, LOCATION_SCHEMA_PATH, asyncResultHandler);
				if (criterion == null) {
					return; // error already handled
				}
				PostgresClient.getInstance(vertxContext.owner(), tenantId).update(LOCATION_TABLE, entity, criterion, false,
						updateReply -> {
							if (updateReply.failed()) {
								String message = logAndSaveError(updateReply.cause());
								asyncResultHandler
												.handle(Future.succeededFuture(
														PutLocationsByIdResponse.respond500WithTextPlain(getErrorResponse(message))));
							} else {
								if (updateReply.result().getUpdated() == 0) {
									asyncResultHandler
											.handle(Future.succeededFuture(PutLocationsByIdResponse.respond404WithTextPlain("Not found")));
									// Not found
								} else {
											asyncResultHandler.handle(Future.succeededFuture(PutLocationsByIdResponse.respond204()));
								}
							}
						});
			} else {
				String message = logAndSaveError(checksResult.cause());
				asyncResultHandler
								.handle(Future
										.succeededFuture(PutLocationsByIdResponse.respond400WithTextPlain("Test Message")));
			}

		});

  }

	@SafeVarargs
	private final CompositeFuture runLocationChecks(Future<String>... futures) {
		List<Future> allFutures = new ArrayList<Future>(Arrays.asList(futures));
		return CompositeFuture.all(allFutures);
	}

	private Future<String> checkIdProvided(Location entity) {

		Future<String> future = Future.succeededFuture();

		String id = entity.getId();
		if (id == null) {
			id = UUID.randomUUID().toString();
			entity.setId(id);
		}

		return future;

	}

	private Future<String> checkIdChange(String id, Location entity) {

		Future<String> future = Future.succeededFuture();

		if (!id.equals(entity.getId())) {
			future = Future.failedFuture("Illegal operation: id cannot be changed");
		}

		return future;

	}

	private Future<String> checkAtLeastOneServicePoint(Location entity) {

		Future<String> future = Future.succeededFuture();

		if (entity.getServicePointIds().size() < 1) {
			future = Future.failedFuture("Bad Request: A location must have at least one Service Point assigned.");
		}

		return future;

	}

	private Future<String> checkPrimaryServicePointRelationship(Location entity) {

		Future<String> future = Future.succeededFuture();

		if (!entity.getServicePointIds().contains(entity.getPrimaryServicePoint())) {
			future = Future
					.failedFuture("Bad Request: A Location's Primary Service point must be included as a Service Point.");
		}

		return future;

	}

}
