package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.folio.rest.impl.StorageHelper.getCQL;
import static org.folio.rest.jaxrs.resource.ItemDamagedStatuses.PostItemDamagedStatusesResponse.headersFor201;
import static org.folio.rest.jaxrs.resource.ItemDamagedStatuses.PostItemDamagedStatusesResponse.respond201WithApplicationJson;
import static org.folio.rest.jaxrs.resource.ItemDamagedStatuses.PostItemDamagedStatusesResponse.respond500WithTextPlain;
import static org.folio.rest.tools.messages.MessageConsts.DeletedCountError;
import static org.folio.rest.tools.messages.MessageConsts.InternalServerError;

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.vertx.core.*;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.folio.rest.jaxrs.model.ItemDamageStatuses;
import org.folio.rest.jaxrs.resource.ItemDamagedStatuses;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ItemDamagedStatusAPI implements ItemDamagedStatuses {
  private static final Logger LOGGER = LoggerFactory.getLogger(ItemDamagedStatusAPI.class);
  public static final String REFERENCE_TABLE = "item_damaged_status";
  private static final String LOCATION_PREFIX = "/item-damaged-statuses/";
  private final Messages messages = Messages.getInstance();
  private PostgresClientFactory pgClientFactory = new PostgresClientFactory();

  @Override
  public void getItemDamagedStatuses(
    String query,
    int offset,
    int limit,
    String lang,
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
        String message = messages.getMessage(lang, InternalServerError);
        Response response = GetItemDamagedStatusesResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });
  }

  private Future<ItemDamageStatuses> searchItemDamagedStatuses(
    String query,
    int offset,
    int limit,
    Map<String, String> okapiHeaders,
    Context vertxContext) throws FieldException {

    Future<Results<ItemDamageStatus>> future = Future.future();

    pgClientFactory.getInstance(vertxContext, okapiHeaders)
      .get(
        REFERENCE_TABLE,
        ItemDamageStatus.class,
        new String[]{"*"},
        getCQL(query, limit, offset, REFERENCE_TABLE),
        true,
        true,
        future.completer()
      );

    return future.map(results ->
      new ItemDamageStatuses()
        .withItemDamageStatuses(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords())
    );
  }

  @Override
  public void postItemDamagedStatuses(
    String lang,
    ItemDamageStatus entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        saveItemDamagedStatus(entity, okapiHeaders, vertxContext)
          .map(it -> respond201WithApplicationJson(it, headersFor201().withLocation(LOCATION_PREFIX + it.getId())))
          .otherwise(ex ->
            ofNullable(PgExceptionUtil.badRequestMessage(ex))
              .map(PostItemDamagedStatusesResponse::respond400WithTextPlain)
              .orElseGet(() -> respond500WithTextPlain(messages.getMessage(lang, InternalServerError)))
          )
          .map(Response.class::cast)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        String message = messages.getMessage(lang, InternalServerError);
        asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(message)));
      }
    });
  }

  private Future<ItemDamageStatus> saveItemDamagedStatus(ItemDamageStatus entity, Map<String, String> okapiHeaders, Context vertxContext) {
    Future<String> future = Future.future();
    if (isNull(entity.getId())) {
      entity.setId(UUID.randomUUID().toString());
    }

    pgClientFactory.getInstance(vertxContext, okapiHeaders)
      .save(REFERENCE_TABLE, entity.getId(), entity, future.completer());

    return future.map(entity::withId);
  }

  @Override
  public void getItemDamagedStatusesById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
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
        String message = messages.getMessage(lang, InternalServerError);
        Response response = GetItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });

  }

  private Future<ItemDamageStatus> getItemDamagedStatus(
    String id,
    Map<String, String> okapiHeaders,
    Context vertxContext) {

    Future<ItemDamageStatus> future = Future.future();
    pgClientFactory.getInstance(vertxContext, okapiHeaders)
      .getById(REFERENCE_TABLE, id, ItemDamageStatus.class, future.completer());
    return future;
  }

  @Override
  public void deleteItemDamagedStatusesById(
    String id,
    String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        deleteItemDamagedStatus(id, okapiHeaders, vertxContext)
          .map(updatedCount -> handleDeleteItemDamagedStatusResult(lang, updatedCount))
          .otherwise(ex -> handleDeleteDamagedStatusException(lang, ex))
          .map(Response.class::cast)
          .onComplete(asyncResultHandler);
      } catch (Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        String message = messages.getMessage(lang, InternalServerError);
        Response response = DeleteItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });
  }

  private Response handleDeleteDamagedStatusException(String lang, Throwable ex) {
    return ofNullable(PgExceptionUtil.badRequestMessage(ex))
      .map(badRequestMessage -> {
        LOGGER.error(badRequestMessage, ex);
        return DeleteItemDamagedStatusesByIdResponse.respond400WithTextPlain(badRequestMessage);
      }).orElseGet(() -> {
        String message = messages.getMessage(lang, InternalServerError);
        LOGGER.error(message, ex);
        return DeleteItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
      });
  }

  private Response handleDeleteItemDamagedStatusResult(String lang, Integer updatedCount) {
    Response response;
    if (updatedCount == 1) {
      response = DeleteItemDamagedStatusesByIdResponse.respond204();
    } else {
      String msg = messages.getMessage(lang, DeletedCountError, 1, updatedCount);
      LOGGER.error(msg);
      response = DeleteItemDamagedStatusesByIdResponse.respond404WithTextPlain(msg);
    }
    return response;
  }

  private Future<Integer> deleteItemDamagedStatus(
    String id,
    Map<String, String> okapiHeaders,
    Context vertxContext) {

    Promise<RowSet<Row>> promise = Promise.promise();
    pgClientFactory.getInstance(vertxContext, okapiHeaders)
      .delete(REFERENCE_TABLE, id, promise.future());
    return promise.future().map(RowSet<Row>::rowCount);
  }

  @Override
  public void putItemDamagedStatusesById(
    String id,
    String lang,
    ItemDamageStatus entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        updateItemDamagedStatus(id, entity, okapiHeaders, vertxContext)
          .map(updatedCount -> handleUpdateItemDamagedStatusResult(lang, updatedCount))
          .otherwise(ex -> handleUpdateItemDamagedStatusesException(lang, ex))
          .map(Response.class::cast)
          .onComplete(asyncResultHandler);
      } catch (Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        String message = messages.getMessage(lang, InternalServerError);
        Response response = PutItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
        asyncResultHandler.handle(succeededFuture(response));
      }
    });

  }

  private Response handleUpdateItemDamagedStatusesException(String lang, Throwable ex) {
    return ofNullable(PgExceptionUtil.badRequestMessage(ex))
      .map(badRequestMessage -> {
        LOGGER.error(badRequestMessage, ex);
        return PutItemDamagedStatusesByIdResponse.respond400WithTextPlain(badRequestMessage);
      }).orElseGet(() -> {
        String message = messages.getMessage(lang, InternalServerError);
        LOGGER.error(message, ex);
        return PutItemDamagedStatusesByIdResponse.respond500WithTextPlain(message);
      });
  }

  private Response handleUpdateItemDamagedStatusResult(String lang, Integer updatedCount) {
    Response response;
    if (updatedCount == 0) {
      String message = messages.getMessage(lang, MessageConsts.NoRecordsUpdated);
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

    Promise<RowSet<Row>> promise = Promise.promise();
    if (isNull(entity.getId())) {
      entity.setId(id);
    }
    pgClientFactory.getInstance(vertxContext, okapiHeaders)
      .update(REFERENCE_TABLE, entity, id, promise.future());

    return promise.future().map(RowSet<Row>::rowCount);
  }
}
