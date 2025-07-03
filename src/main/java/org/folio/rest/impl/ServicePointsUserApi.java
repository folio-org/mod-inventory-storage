package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ServicePointsUser;
import org.folio.rest.jaxrs.model.ServicePointsUsers;

public class ServicePointsUserApi extends BaseApi<ServicePointsUser, ServicePointsUsers>
  implements org.folio.rest.jaxrs.resource.ServicePointsUsers {

  private static final String SERVICE_POINT_USER_TABLE = "service_point_user";

  @Validate
  @Override
  public void getServicePointsUsers(String query, String totalRecords, int offset, int limit,
                                    Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetServicePointsUsersResponse.class);
  }

  @Validate
  @Override
  public void postServicePointsUsers(ServicePointsUser entity,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostServicePointsUsersResponse.class);
  }

  @Validate
  @Override
  public void deleteServicePointsUsers(Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntities(okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Validate
  @Override
  public void getServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {
    getEntityById(servicePointsUserId, okapiHeaders, asyncResultHandler, vertxContext,
      GetServicePointsUsersByServicePointsUserIdResponse.class);
  }

  @Validate
  @Override
  public void deleteServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                            Map<String, String> okapiHeaders,
                                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                                            Context vertxContext) {
    deleteEntityById(servicePointsUserId, okapiHeaders, asyncResultHandler, vertxContext,
      DeleteServicePointsUsersByServicePointsUserIdResponse.class);
  }

  @Validate
  @Override
  public void putServicePointsUsersByServicePointsUserId(String servicePointsUserId,
                                                         ServicePointsUser entity,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {
    putEntityById(servicePointsUserId, entity, okapiHeaders, asyncResultHandler, vertxContext,
      PutServicePointsUsersByServicePointsUserIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return SERVICE_POINT_USER_TABLE;
  }

  @Override
  protected Class<ServicePointsUser> getEntityClass() {
    return ServicePointsUser.class;
  }

  @Override
  protected Class<ServicePointsUsers> getEntityCollectionClass() {
    return ServicePointsUsers.class;
  }
}
