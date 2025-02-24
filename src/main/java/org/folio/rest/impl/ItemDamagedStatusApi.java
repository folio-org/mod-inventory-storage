package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.folio.rest.impl.StorageHelper.getCql;
import static org.folio.rest.jaxrs.resource.ItemDamagedStatuses.PostItemDamagedStatusesResponse.headersFor201;
import static org.folio.rest.jaxrs.resource.ItemDamagedStatuses.PostItemDamagedStatusesResponse.respond201WithApplicationJson;
import static org.folio.rest.jaxrs.resource.ItemDamagedStatuses.PostItemDamagedStatusesResponse.respond500WithTextPlain;
import static org.folio.rest.tools.messages.MessageConsts.DeletedCountError;
import static org.folio.rest.tools.messages.MessageConsts.InternalServerError;
import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.folio.rest.jaxrs.model.ItemDamageStatuses;
import org.folio.rest.jaxrs.resource.ItemDamagedStatuses;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

public class ItemDamagedStatusApi implements ItemDamagedStatuses {
  public static final String REFERENCE_TABLE = "item_damaged_status";
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String LOCATION_PREFIX = "/item-damaged-statuses/";
  private final Messages messages = Messages.getInstance();
  private PostgresClientFactory pgClientFactory = new PostgresClientFactory();

  @Validate
  @Override
  public void getItemDamagedStatuses(String query, String totalRecords, int offset, int limit,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        searchItemDamagedStatuses(query, offset, limit, okapiHeaders, vertxContext)
          .map(GetItemDamagedStatusesResponse::respond200WithApplicationJson)
          .otherwise(ex -> {
            LOGGER.error(ex.getMessage(), ex);
            return GetItemDamagedStatusesResponse.respond400WithTextPlain(ex.getMessage());
          })
          .map(Response.class::cast)
          .onComplete(asyncResultHandler);
      } catch (Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        String message = messages.getMessage(DEFAULT_LANGUAGE, InternalServerError);
        Response response = GetItemDamagedStatusesResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });
  }

  @Validate
  @Override
  public void postItemDamagedStatuses(ItemDamageStatus entity, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        saveItemDamagedStatus(entity, okapiHeaders, vertxContext)
          .map(it -> respond201WithApplicationJson(it, headersFor201().withLocation(LOCATION_PREFIX + it.getId())))
          .otherwise(ex ->
            ofNullable(PgExceptionUtil.badRequestMessage(ex))
              .map(PostItemDamagedStatusesResponse::respond400WithTextPlain)
              .orElseGet(() -> respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, InternalServerError)))
          )
          .map(Response.class::cast)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        String message = messages.getMessage(DEFAULT_LANGUAGE, InternalServerError);
        asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void getItemDamagedStatusesById(String id, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        getItemDamagedStatus(id, okapiHeaders, vertxContext)
          .map(entity ->
            ofNullable(entity)
              .map(GetItemDamagedStatusesByIdResponse::respond200WithApplicationJson)
              .orElseGet(() -> GetItemDamagedStatusesByIdResponse.respond404WithTextPlain("Not found"))
          )
          .map(Response.class::cast)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        String message = messages.getMessage(DEFAULT_LANGUAGE, InternalServerError);
        Response response = GetItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });

  }

  @Validate
  @Override
  public void deleteItemDamagedStatusesById(String id, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        deleteItemDamagedStatus(id, okapiHeaders, vertxContext)
          .map(this::handleDeleteItemDamagedStatusResult)
          .otherwise(this::handleDeleteDamagedStatusException)
          .onComplete(asyncResultHandler);
      } catch (Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        String message = messages.getMessage(DEFAULT_LANGUAGE, InternalServerError);
        Response response = DeleteItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });
  }

  @Validate
  @Override
  public void putItemDamagedStatusesById(String id, ItemDamageStatus entity,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        updateItemDamagedStatus(id, entity, okapiHeaders, vertxContext)
          .map(this::handleUpdateItemDamagedStatusResult)
          .otherwise(this::handleUpdateItemDamagedStatusesException)
          .onComplete(asyncResultHandler);
      } catch (Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        String message = messages.getMessage(DEFAULT_LANGUAGE, InternalServerError);
        Response response = PutItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });

  }

  // protected is needed for unit test
  protected Future<ItemDamageStatuses> searchItemDamagedStatuses(
    String query,
    int offset,
    int limit,
    Map<String, String> okapiHeaders,
    Context vertxContext) throws FieldException {

    CQLWrapper cql = getCql(query, limit, offset, REFERENCE_TABLE);
    return Future.<Results<ItemDamageStatus>>future(promise -> pgClientFactory
      .getInstance(vertxContext, okapiHeaders)
      .get(REFERENCE_TABLE,
        ItemDamageStatus.class,
        new String[] {"*"},
        cql,
        true,
        true,
        promise)).map(results -> new ItemDamageStatuses()
      .withItemDamageStatuses(results.getResults())
      .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  // protected is needed for unit testing
  protected Future<ItemDamageStatus> getItemDamagedStatus(
    String id,
    Map<String, String> okapiHeaders,
    Context vertxContext) {
    return Future.future(promise -> pgClientFactory
      .getInstance(vertxContext, okapiHeaders)
      .getById(REFERENCE_TABLE, id, ItemDamageStatus.class, promise));
  }

  private Future<ItemDamageStatus> saveItemDamagedStatus(ItemDamageStatus entity, Map<String, String> okapiHeaders,
                                                         Context vertxContext) {
    if (isNull(entity.getId())) {
      entity.setId(UUID.randomUUID().toString());
    }
    return Future.<String>future(promise -> pgClientFactory
        .getInstance(vertxContext, okapiHeaders)
        .save(REFERENCE_TABLE, entity.getId(), entity, promise))
      .map(entity::withId);
  }

  private Response handleDeleteDamagedStatusException(Throwable ex) {
    return ofNullable(PgExceptionUtil.badRequestMessage(ex))
      .map(badRequestMessage -> {
        LOGGER.error(badRequestMessage, ex);
        return DeleteItemDamagedStatusesByIdResponse.respond400WithTextPlain(badRequestMessage);
      }).orElseGet(() -> {
        String message = messages.getMessage(Messages.DEFAULT_LANGUAGE, InternalServerError);
        LOGGER.error(message, ex);
        return DeleteItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
      });
  }

  private Response handleDeleteItemDamagedStatusResult(Integer updatedCount) {
    Response response;
    if (updatedCount == 1) {
      response = DeleteItemDamagedStatusesByIdResponse.respond204();
    } else {
      String msg = messages.getMessage(Messages.DEFAULT_LANGUAGE, DeletedCountError, 1, updatedCount);
      LOGGER.error(msg);
      response = DeleteItemDamagedStatusesByIdResponse.respond404WithTextPlain(msg);
    }
    return response;
  }

  private Future<Integer> deleteItemDamagedStatus(
    String id,
    Map<String, String> okapiHeaders,
    Context vertxContext) {
    return Future.<RowSet<Row>>future(promise -> pgClientFactory
        .getInstance(vertxContext, okapiHeaders)
        .delete(REFERENCE_TABLE, id, promise))
      .map(RowSet<Row>::rowCount);
  }

  private Response handleUpdateItemDamagedStatusesException(Throwable ex) {
    return ofNullable(PgExceptionUtil.badRequestMessage(ex))
      .map(badRequestMessage -> {
        LOGGER.error(badRequestMessage, ex);
        return PutItemDamagedStatusesByIdResponse.respond400WithTextPlain(badRequestMessage);
      }).orElseGet(() -> {
        String message = messages.getMessage(Messages.DEFAULT_LANGUAGE, InternalServerError);
        LOGGER.error(message, ex);
        return PutItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
      });
  }

  private Response handleUpdateItemDamagedStatusResult(Integer updatedCount) {
    Response response;
    if (updatedCount == 0) {
      String message = messages.getMessage(Messages.DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated);
      LOGGER.error(message);
      response = PutItemDamagedStatusesByIdResponse.respond404WithTextPlain(message);
    } else {
      response = PutItemDamagedStatusesByIdResponse.respond204();
    }
    return response;
  }

  private Future<Integer> updateItemDamagedStatus(
    String id,
    ItemDamageStatus entity,
    Map<String, String> okapiHeaders,
    Context vertxContext) {

    if (isNull(entity.getId())) {
      entity.setId(id);
    }
    return Future.<RowSet<Row>>future(promise -> pgClientFactory
        .getInstance(vertxContext, okapiHeaders)
        .update(REFERENCE_TABLE, entity, id, promise))
      .map(RowSet<Row>::rowCount);
  }
}
