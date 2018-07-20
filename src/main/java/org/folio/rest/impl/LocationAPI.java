package org.folio.rest.impl;

import static org.folio.rest.impl.StorageHelper.getCQL;
import static org.folio.rest.impl.StorageHelper.getTenant;
import static org.folio.rest.impl.StorageHelper.idCriterion;
import static org.folio.rest.impl.StorageHelper.isInUse;
import static org.folio.rest.impl.StorageHelper.logAndSaveError;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.resource.LocationsResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 *
 * @author kurt
 */
public class LocationAPI implements LocationsResource {
  private final Messages messages = Messages.getInstance();
  public static final String LOCATION_TABLE = "location";
	public static final String SERVICE_POINT_TABLE = "service_point";
  private final Logger logger = LoggerFactory.getLogger(LocationAPI.class);
  public static final String URL_PREFIX = "/locations";
  public static final String LOCATION_SCHEMA_PATH = "apidocs/raml/location.json";
	public static final String SERVICEPOINT_SCHEMA_PATH = "apidocs/raml/servicepoint.json";
  public static final String ID_FIELD_NAME = "'id'";

  @Override
  public void deleteLocations(          String lang,
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
            LocationsResource.DeleteLocationsResponse.noContent()
              .build()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(
            LocationsResource.DeleteLocationsResponse.
              withPlainInternalServerError(reply.cause().getMessage())));
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
        GetLocationsResponse.withPlainBadRequest(message)));
      return;
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(LOCATION_TABLE, Location.class,
        new String[]{"*"}, cql, true, true, reply -> {
          // netbeans, please indent here!
          if (reply.failed()) {
            String message = logAndSaveError(reply.cause());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsResponse.withPlainBadRequest(message)));
          } else {
            Locations shelfLocations = new Locations();
            List<Location> shelfLocationsList = (List<Location>) reply.result().getResults();
            shelfLocations.setLocations(shelfLocationsList);
            shelfLocations.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsResponse.withJsonOK(shelfLocations)));
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
    String id = entity.getId();
    if (id == null) {
      id = UUID.randomUUID().toString();
      entity.setId(id);
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .save(LOCATION_TABLE, id, entity, reply -> {
        if (reply.failed()) {
          String message = logAndSaveError(reply.cause());
          if (message != null
            && message.contains("duplicate key value violates unique constraint")) {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationsResponse.withJsonUnprocessableEntity(
                ValidationHelper.createValidationErrorMessage(
                  "shelflocation", entity.getId(),
                  "Location already exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              PostLocationsResponse.withPlainInternalServerError(message)));
          }
        } else {
          Object responseObject = reply.result();
          entity.setId((String) responseObject);
          OutStream stream = new OutStream();
          stream.setData(entity);
          asyncResultHandler.handle(Future.succeededFuture(
            PostLocationsResponse.withJsonCreated(URL_PREFIX + responseObject, stream)));
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
            GetLocationsByIdResponse.withPlainInternalServerError(message)));
        } else {
          List<Location> locationList = (List<Location>) getReply.result().getResults();
          if (locationList.isEmpty()) {
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsByIdResponse.withPlainNotFound(
                messages.getMessage(lang, MessageConsts.ObjectDoesNotExist))));
          } else if (locationList.size() > 1) {
            String message = "Multiple locations found with the same id";
            logger.error(message);
            asyncResultHandler.handle(Future.succeededFuture(
              GetLocationsByIdResponse.withPlainInternalServerError(message)));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(GetLocationsByIdResponse.withJsonOK(locationList.get(0))));
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

		PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);

		pgClient.startTx(connection -> {
						
			if (performCascade(pgClient, connection, id)) {
				pgClient.delete(connection, LOCATION_TABLE, criterion, deleteReply -> {
					if (deleteReply.failed()) {
						pgClient.rollbackTx(connection, rollback -> {
							logAndSaveError(deleteReply.cause());
							if (isInUse(deleteReply.cause().getMessage())) {
								asyncResultHandler.handle(Future.succeededFuture(
										DeleteLocationsByIdResponse.withPlainBadRequest("Location is in use, can not be deleted")));
							} else {
								asyncResultHandler
										.handle(Future.succeededFuture(DeleteLocationsByIdResponse.withPlainNotFound("Not found")));
							}
						});
					} else {
						pgClient.endTx(connection, done -> {
							asyncResultHandler.handle(Future.succeededFuture(DeleteLocationsByIdResponse.withNoContent()));
						});
					}
				});
			} else {
				asyncResultHandler.handle(Future.failedFuture("Failed to cascade delete."));
			}

		});
		
  }

	private boolean performCascade(PostgresClient pgClient, AsyncResult<Object> connection, String id) {

		boolean succeded = true;

		try {
			String query = "locationIds=" + id;
			CQL2PgJSON cql2pgJson = new CQL2PgJSON(SERVICE_POINT_TABLE + ".jsonb");
			CQLWrapper cql = new CQLWrapper(cql2pgJson, query);

			pgClient.get(SERVICE_POINT_TABLE, Servicepoint.class, new String[] { "*" }, cql, true, false, reply -> {

				@SuppressWarnings("unchecked")
				List<Servicepoint> sp = (List<Servicepoint>) reply.result().getResults();

				sp.forEach(s -> {
					Criteria idCrit = new Criteria().addField("'id'").setOperation("=").setValue(s.getId());
					s.getLocationIds().remove(id);
					pgClient.update(SERVICE_POINT_TABLE, s, new Criterion(idCrit), false, updateReply -> {
					});
				});

			});
		} catch (Exception e) {
			pgClient.rollbackTx(connection, rollback -> {
				logAndSaveError(e.getCause());
			});
			succeded = false;
		}

		return succeded;

	}

  @Override
  public void putLocationsById(
    String id,
    String lang,
    Location entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>>asyncResultHandler,
    Context vertxContext) {

    if (!id.equals(entity.getId())) {
      String message = "Illegal operation: id cannot be changed";
      asyncResultHandler.handle(Future.succeededFuture(
        PutLocationsByIdResponse.withPlainBadRequest(message)));
      return;
    }
    String tenantId = getTenant(okapiHeaders);
    Criterion criterion = idCriterion(id, LOCATION_SCHEMA_PATH, asyncResultHandler);
    if (criterion == null) {
      return; // error already handled
    }
    PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
      LOCATION_TABLE, entity, criterion, false, updateReply -> {
        if (updateReply.failed()) {
          String message = logAndSaveError(updateReply.cause());
          asyncResultHandler.handle(Future.succeededFuture(
            PutLocationsByIdResponse.withPlainInternalServerError(message)));
        } else {
          if (updateReply.result().getUpdated() == 0) {
            asyncResultHandler.handle(Future.succeededFuture(
              PutLocationsByIdResponse.withPlainNotFound("Not found")));
            //Not found
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              PutLocationsByIdResponse.withNoContent()));
          }
        }
      });
  }

}
