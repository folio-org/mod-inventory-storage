package org.folio.rest.impl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Servicepointsuser;
import org.folio.rest.jaxrs.resource.ServicePointsResource;
import org.folio.rest.jaxrs.resource.ServicePointsUsersResource;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

public class ServicePointsUserAPI implements ServicePointsUsersResource {
  
  public static final Logger logger = LoggerFactory.getLogger(
          ServicePointsUserAPI.class);
  public static final String SERVICE_POINT_USER_TABLE = "service_point_user";
  public static final String LOCATION_PREFIX = "/service-points-users/";
  public static final String ID_FIELD = "'id'";

  PostgresClient getPGClient(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId);
  }

  private String getErrorResponse(String response) {
    //Check to see if we're suppressing messages or not
    return response;
  }

  private String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  private String getTenant(Map<String, String> headers)  {
    return TenantTool.calculateTenantId(headers.get(
            RestVerticle.OKAPI_HEADER_TENANT));
  }

  private CQLWrapper getCQL(String query, int limit, int offset,
          String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit))
            .setOffset(new Offset(offset));
  }

  private boolean isDuplicate(String errorMessage){
    if(errorMessage != null && errorMessage.contains(
            "duplicate key value violates unique constraint")){
      return true;
    }
    return false;
  }

  private boolean isCQLError(Throwable err) {
    if(err.getCause() != null && err.getCause().getClass().getSimpleName()
            .endsWith("CQLParseException")) {
      return true;
    }
    return false;
  }

  @Override
  public void deleteServicePointsUsers(String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void getServicePointsUsers(String query, int offset, int limit,
      String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void postServicePointsUsers(String lang, Servicepointsuser entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    try {
      String tenantId = getTenant(okapiHeaders);
      String id = entity.getId();
      if(id == null){
        id = UUID.randomUUID().toString();
        entity.setId(id);
      }
      PostgresClient pgClient = getPGClient(vertxContext, tenantId);
      pgClient.save(SERVICE_POINT_USER_TABLE, id, entity, saveReply -> {
        if(saveReply.failed()) {
          String message = logAndSaveError(saveReply.cause());
          if(isDuplicate(message)) {
            asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsUsersResponse.withJsonUnprocessableEntity(
                ValidationHelper.createValidationErrorMessage("userId",
                entity.getUserId(), "Service Point User Exists"))));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsUsersResponse.withPlainInternalServerError(
                getErrorResponse(message))));
          }
        } else {
          String ret = saveReply.result();
            entity.setId(ret);
            OutStream stream = new OutStream();
            stream.setData(entity);
            asyncResultHandler.handle(Future.succeededFuture(
                PostServicePointsUsersResponse.withJsonCreated(LOCATION_PREFIX
                + ret, stream)));
        }
      });      
    } catch(Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(Future.succeededFuture(
          PostServicePointsUsersResponse.withPlainInternalServerError(
          getErrorResponse(message))));
    }
  }

  @Override
  public void getServicePointsUsersByServicepointsuserId(String servicepointsuserId,
      String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deleteServicePointsUsersByServicepointsuserId(String servicepointsuserId,
      String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void putServicePointsUsersByServicepointsuserId(String servicepointsuserId,
      String lang, Servicepointsuser entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
}
