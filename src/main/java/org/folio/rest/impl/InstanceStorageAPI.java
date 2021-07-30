package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import static org.folio.persist.InstanceMarcRepository.INSTANCE_SOURCE_MARC_TABLE;
import static org.folio.persist.InstanceRelationshipRepository.INSTANCE_RELATIONSHIP_TABLE;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.impl.PrecedingSucceedingTitleAPI.PRECEDING_SUCCEEDING_TITLE_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import java.util.List;
import java.util.Map;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationships;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitles;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.services.instance.InstanceService;

public class InstanceStorageAPI implements InstanceStorage {
  private static final Logger log = LogManager.getLogger();

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  private final Messages messages = Messages.getInstance();

  PreparedCQL handleCQL(String query, int limit, int offset) throws FieldException {
    return new PreparedCQL(INSTANCE_TABLE, query, limit, offset);
  }

  private static CQLWrapper createCQLWrapper(
    String query,
    int limit,
    int offset,
    String tableName) throws FieldException {

    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");

    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }

  @Override
  public void getInstanceStorageInstances(
    @DefaultValue("0") @Min(0L) @Max(1000L) int offset,
    @DefaultValue("10") @Min(1L) @Max(100L) int limit,
    String query,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (PgUtil.checkOptimizedCQL(query, "title") != null) { // Until RMB-573 is fixed
      try {
        PreparedCQL preparedCql = handleCQL(query, limit, offset);
        PgUtil.getWithOptimizedSql(preparedCql.getTableName(), Instance.class, Instances.class,
          "title", query, offset, limit,
          okapiHeaders, vertxContext, GetInstanceStorageInstancesResponse.class, asyncResultHandler);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetInstanceStorageInstancesResponse.
            respond500WithTextPlain(e.getMessage())));
      }
      return;
    }
    PgUtil.streamGet(INSTANCE_TABLE, Instance.class, query, offset, limit, null,
      "instances", routingContext, okapiHeaders, vertxContext);
  }

  @Override
  public void postInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .createInstance(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void deleteInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .deleteAllInstances()
      .onSuccess(notUsed -> asyncResultHandler.handle(
        succeededFuture(DeleteInstanceStorageInstancesResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void getInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      String[] fieldList = {"*"};

      PreparedCQL preparedCql = handleCQL(String.format("id==%s", instanceId), 1, 0);
      CQLWrapper cql = preparedCql.getCqlWrapper();

      log.info(String.format("SQL generated from CQL: %s", cql.toString()));

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(preparedCql.getTableName(), Instance.class, fieldList, cql, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<Instance> instanceList = reply.result().getResults();
                  if (instanceList.size() == 1) {
                    Instance instance = instanceList.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        GetInstanceStorageInstancesByInstanceIdResponse.
                          respond200WithApplicationJson(instance)));
                  }
                  else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      GetInstanceStorageInstancesByInstanceIdResponse.
                        respond404WithTextPlain("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      GetInstanceStorageInstancesByInstanceIdResponse.
                        respond500WithTextPlain(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstancesByInstanceIdResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstancesByInstanceIdResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetInstanceStorageInstancesByInstanceIdResponse.
          respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .deleteInstance(instanceId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void putInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    new InstanceService(vertxContext, okapiHeaders)
      .updateInstance(instanceId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void deleteInstanceStorageInstancesSourceRecordByInstanceId(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
        DeleteInstanceStorageInstancesSourceRecordByInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void getInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(INSTANCE_SOURCE_MARC_TABLE, MarcJson.class, instanceId,
      okapiHeaders, vertxContext,
      GetInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(INSTANCE_SOURCE_MARC_TABLE, instanceId, okapiHeaders, vertxContext,
        DeleteInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putInstanceStorageInstancesSourceRecordMarcJsonByInstanceId(
      @NotNull String instanceId,
      @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
      MarcJson entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext)  {

    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    postgresClient.upsert(INSTANCE_SOURCE_MARC_TABLE, instanceId, entity, reply -> {
      if (reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
          PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse.respond204()));
        return;
      }
      if (PgExceptionUtil.isForeignKeyViolation(reply.cause())
        && reply.cause().getMessage().contains(INSTANCE_SOURCE_MARC_TABLE)) {
        asyncResultHandler.handle(Future.succeededFuture(
            PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
            .respond404WithTextPlain(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
        PutInstanceStorageInstancesSourceRecordMarcJsonByInstanceIdResponse
          .respond500WithTextPlain(reply.cause().getMessage())));
    });
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Override
  public void getInstanceStorageInstancesSourceRecordModsByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      GetInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
        .respond500WithTextPlain("Not implemented yet.")));
  }

  /**
   * Example stub showing how other formats might get implemented.
   */
  @Override
  public void putInstanceStorageInstancesSourceRecordModsByInstanceId(
      String instanceId, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      PutInstanceStorageInstancesSourceRecordModsByInstanceIdResponse
        .respond500WithTextPlain("Not implemented yet.")));
  }

  @Override
  public void getInstanceStorageInstanceRelationships(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    try {
      vertxContext.runOnContext(v -> {
        try {

          String[] fieldList = {"*"};

          CQLWrapper cql = createCQLWrapper(query, limit, offset, INSTANCE_RELATIONSHIP_TABLE);

          log.info(String.format("SQL generated from CQL: %s", cql.toString()));

          postgresClient.get(INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  List<InstanceRelationship> instanceRelationships = reply.result().getResults();

                  InstanceRelationships instanceList = new InstanceRelationships();
                  instanceList.setInstanceRelationships(instanceRelationships);
                  instanceList.setTotalRecords(reply.result().getResultInfo().getTotalRecords());

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                          GetInstanceStorageInstanceRelationshipsResponse.respond200WithApplicationJson(instanceList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetInstanceStorageInstanceRelationshipsResponse.
                      respond500WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetInstanceStorageInstanceRelationshipsResponse.
                    respond500WithTextPlain(e.getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetInstanceStorageInstanceRelationshipsResponse.
              respond500WithTextPlain(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetInstanceStorageInstanceRelationshipsResponse.
          respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void postInstanceStorageInstanceRelationships(String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(INSTANCE_RELATIONSHIP_TABLE, entity, okapiHeaders, vertxContext,
        PostInstanceStorageInstanceRelationshipsResponse.class, asyncResultHandler);
  }

  @Override
  public void getInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deleteInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PostgresClient postgresClient =
        PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));

    postgresClient.delete(INSTANCE_RELATIONSHIP_TABLE, relationshipId, reply -> {
      if (! reply.succeeded()) {
        asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse
            .respond500WithTextPlain(reply.cause().getMessage())));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstanceRelationshipsByRelationshipIdResponse.respond204()));
    });
  }

  @Override
  public void putInstanceStorageInstanceRelationshipsByRelationshipId(String relationshipId, String lang, InstanceRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(relationshipId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
        INSTANCE_RELATIONSHIP_TABLE, entity, relationshipId,
        reply -> {
          try {
            if (reply.succeeded()) {
              if (reply.result().rowCount() == 0) {
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                    .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
              } else{
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                    .respond204()));
              }
            } else {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              if (msg == null) {
                asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                   .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
                  .respond400WithTextPlain(msg)));
            }
          } catch (Exception e) {
            asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
               .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
          }

        });
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(PutInstanceStorageInstanceRelationshipsByRelationshipIdResponse
           .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceId(@NotNull String instanceId,
                                                                               PrecedingSucceedingTitles entity,
                                                                               Map<String, String> okapiHeaders,
                                                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                                                               Context vertxContext) {

    var titles = entity.getPrecedingSucceedingTitles();
    boolean areValidTitles = validatePrecedingSucceedingTitles(titles, instanceId, asyncResultHandler);
    if (areValidTitles) {
      var cqlQuery = String.format("succeedingInstanceId==(%s) or precedingInstanceId==(%s)", instanceId, instanceId);
      PgUtil.delete(PRECEDING_SUCCEEDING_TITLE_TABLE, cqlQuery, okapiHeaders, vertxContext,
          PutInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceIdResponse.class)
        .compose(response -> saveCollection(titles, okapiHeaders, vertxContext))
        .onComplete(asyncResultHandler);
    }
  }

  private boolean validatePrecedingSucceedingTitles(List<PrecedingSucceedingTitle> precedingSucceedingTitles,
                                                    String instanceId,
                                                    Handler<AsyncResult<Response>> asyncResultHandler) {
    boolean areValidTitles = true;
    for (PrecedingSucceedingTitle precedingSucceedingTitle : precedingSucceedingTitles) {
      if (titleIsLinkedToInstanceId(precedingSucceedingTitle, instanceId)) {
        var validationErrorMessage =
          createValidationErrorMessage("precedingInstanceId or succeedingInstanceId", "",
            String.format("The precedingInstanceId or succeedingInstanceId should contain instanceId [%s]", instanceId));
        asyncResultHandler.handle(
          Future.succeededFuture(PutInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceIdResponse
            .respond422WithApplicationJson(validationErrorMessage)));
        areValidTitles = false;
      }
    }
    return areValidTitles;
  }

  private boolean titleIsLinkedToInstanceId(PrecedingSucceedingTitle precedingSucceedingTitle, String instanceId) {
    return !instanceId.equals(precedingSucceedingTitle.getPrecedingInstanceId()) && !instanceId.equals(
      precedingSucceedingTitle.getSucceedingInstanceId());
  }

  private Future<Response> saveCollection(List<PrecedingSucceedingTitle> entities,
                                          Map<String, String> okapiHeaders, Context vertxContext) {
    Promise<Response> promise = Promise.promise();

    try {
      MetadataUtil.populateMetadata(entities, okapiHeaders);
      PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
      Handler<AsyncResult<RowSet<Row>>> replyHandler = result -> {
        if (result.failed()) {
          var errorMessage = result.cause().getMessage();
          PutInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceIdResponse response;
          if (ValidationHelper.isFKViolation(errorMessage)) {
            response = PutInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceIdResponse
              .respond404WithTextPlain("Instance not found");
          } else {
            response = PutInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceIdResponse
              .respond500WithTextPlain(errorMessage);
          }
          promise.complete(response);
        } else {
          promise.complete(PutInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceIdResponse.respond204());
        }
      };
      postgresClient.saveBatch(PRECEDING_SUCCEEDING_TITLE_TABLE, entities, replyHandler);
    } catch (ReflectiveOperationException e) {
      promise.complete(PutInstanceStorageInstancesPrecedingSucceedingTitlesByInstanceIdResponse.respond500WithTextPlain(e.getMessage()));
    }
    return promise.future();
  }

  static class PreparedCQL {
    private final String tableName;
    private final CQLWrapper cqlWrapper;

    public PreparedCQL(String tableName, String query, int limit, int offset)
        throws FieldException {
      this.tableName = tableName;
      this.cqlWrapper = createCQLWrapper(query, limit, offset, tableName);
    }

    public String getTableName() {
      return tableName;
    }

    public CQLWrapper getCqlWrapper() {
      return cqlWrapper;
    }
  }
}
