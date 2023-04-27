package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ServicePointsUser;
import org.folio.rest.jaxrs.model.ServicePointsUsers;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

public class ServicePointsUserApi implements org.folio.rest.jaxrs.resource.ServicePointsUsers {
  private static final Logger logger = LogManager.getLogger();
  private static final String SERVICE_POINT_USER_TABLE = "service_point_user";

  @Validate
  @Override
  public void getServicePointsUsers(String query, int offset, int limit,
                                    String lang, Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.get(SERVICE_POINT_USER_TABLE, ServicePointsUser.class, ServicePointsUsers.class,
      query, offset, limit, okapiHeaders, vertxContext, GetServicePointsUsersResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void postServicePointsUsers(String lang, ServicePointsUser entity,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.post(SERVICE_POINT_USER_TABLE, entity, okapiHeaders, vertxContext,
      PostServicePointsUsersResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteServicePointsUsers(String lang, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    final PostgresClient pgClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    pgClient.execute(getDeleteAllQuery(okapiHeaders), mutateReply -> {
      if (mutateReply.succeeded()) {
        asyncResultHandler.handle(succeededFuture(DeleteServicePointsUsersResponse.respond204()));
      } else {
        final String message = mutateReply.cause().getMessage();

        logger.warn("Unable to remove all records", mutateReply.cause());

        asyncResultHandler.handle(succeededFuture(DeleteServicePointsUsersResponse.respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void getServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                         String lang, Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {

    PgUtil.getById(SERVICE_POINT_USER_TABLE, ServicePointsUser.class, servicePointsUserId,
      okapiHeaders, vertxContext, GetServicePointsUsersByServicePointsUserIdResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                            String lang, Map<String, String> okapiHeaders,
                                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                                            Context vertxContext) {

    PgUtil.deleteById(SERVICE_POINT_USER_TABLE, servicePointsUserId, okapiHeaders, vertxContext,
      DeleteServicePointsUsersByServicePointsUserIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                         String lang, ServicePointsUser entity,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {

    PgUtil.put(SERVICE_POINT_USER_TABLE, entity, servicePointsUserId, okapiHeaders, vertxContext,
      PutServicePointsUsersByServicePointsUserIdResponse.class, asyncResultHandler);
  }

  private String getDeleteAllQuery(Map<String, String> okapiHeaders) {
    return String.format("DELETE FROM %s_mod_inventory_storage.%s", tenantId(okapiHeaders),
      SERVICE_POINT_USER_TABLE);
  }
}
