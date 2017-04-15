package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.resource.InstanceStorageResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class InstanceStorageAPI implements InstanceStorageResource {

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final String BLANK_TENANT_MESSAGE = "Tenant Must Be Provided";

  @Override
  public void getInstanceStorageInstances(
    @DefaultValue("0") @Min(0L) @Max(1000L) int offset,
    @DefaultValue("10") @Min(1L) @Max(100L) int limit,
    String query,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

          String[] fieldList = {"*"};

          CQL2PgJSON cql2pgJson = new CQL2PgJSON("instance.jsonb");
          CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));

          postgresClient.get("instance", Instance.class, fieldList, cql,
            true, false, reply -> {
              try {
                if(reply.succeeded()) {
                  List<Instance> instances = (List<Instance>) reply.result()[0];

                  Instances instanceList = new Instances();
                  instanceList.setInstances(instances);
                  instanceList.setTotalRecords((Integer)reply.result()[1]);

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.GetInstanceStorageInstancesResponse.
                      withJsonOK(instanceList)));
                }
                else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.GetInstanceStorageInstancesResponse.
                      withPlainInternalServerError(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstancesResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.GetInstanceStorageInstancesResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.GetInstanceStorageInstancesResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void postInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {

          if(entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
          }
          else {
            if(isUUID(entity.getId())) {
              io.vertx.core.Future.succeededFuture(
                InstanceStorageResource.PostInstanceStorageInstancesResponse
                  .withPlainBadRequest("ID must be a UUID"));
            }
          }

          postgresClient.save("instance", entity.getId(), entity,
            reply -> {
              try {
                if(reply.succeeded()) {
                  OutStream stream = new OutStream();
                  stream.setData(entity);

                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.PostInstanceStorageInstancesResponse
                        .withJsonCreated(reply.result(), stream)));
                }
                else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                      InstanceStorageResource.PostInstanceStorageInstancesResponse
                        .withPlainBadRequest("ID must be a UUID")));
                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(
                    InstanceStorageResource.PostInstanceStorageInstancesResponse
                      .withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.PostInstanceStorageInstancesResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.PostInstanceStorageInstancesResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstances(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s.instance",
          tenantId, "inventory_storage"),
          reply -> {
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              InstanceStorageResource.DeleteInstanceStorageInstancesResponse
                .noContent().build()));
          });
      }
      catch(Exception e) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          InstanceStorageResource.DeleteInstanceStorageInstancesResponse
            .withPlainInternalServerError(e.getMessage())));
      }
    });
  }

  @Override
  public void getInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(instanceId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get("instance", Instance.class, criterion, true, false,
            reply -> {
              try {
                if (reply.succeeded()) {
                  List<Instance> instanceList = (List<Instance>) reply.result()[0];
                  if (instanceList.size() == 1) {
                    Instance instance = instanceList.get(0);

                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(
                        InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                          withJsonOK(instance)));
                  }
                  else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                        withPlainNotFound("Not Found")));
                  }
                } else {
                  asyncResultHandler.handle(
                    Future.succeededFuture(
                      InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                        withPlainInternalServerError(reply.cause().getMessage())));

                }
              } catch (Exception e) {
                e.printStackTrace();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          e.printStackTrace();
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void deleteInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(instanceId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete("instance", criterion,
            reply -> {
              if(reply.succeeded()) {
                asyncResultHandler.handle(
                  Future.succeededFuture(
                    DeleteInstanceStorageInstancesByInstanceIdResponse
                      .withNoContent()));
              }
              else {
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteInstanceStorageInstancesByInstanceIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteInstanceStorageInstancesByInstanceIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStorageInstancesByInstanceIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void putInstanceStorageInstancesByInstanceId(
    @NotNull String instanceId,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Instance entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(instanceId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get("instance", Instance.class, criterion, true, false,
            reply -> {
              if(reply.succeeded()) {
                List<Instance> itemList = (List<Instance>) reply.result()[0];

                if (itemList.size() == 1) {
                  try {
                    postgresClient.update("instance", entity, criterion,
                      true,
                      update -> {
                        try {
                          if(update.succeeded()) {
                            OutStream stream = new OutStream();
                            stream.setData(entity);

                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutInstanceStorageInstancesByInstanceIdResponse
                                  .withNoContent()));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                PutInstanceStorageInstancesByInstanceIdResponse
                                  .withPlainInternalServerError(
                                    update.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                        }
                      });
                  } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutInstanceStorageInstancesByInstanceIdResponse
                        .withPlainInternalServerError(e.getMessage())));
                  }
              }
              else {
                try {
                  postgresClient.save("instance", entity.getId(), entity,
                    save -> {
                      try {
                        if(save.succeeded()) {
                          OutStream stream = new OutStream();
                          stream.setData(entity);

                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .withNoContent()));
                        }
                        else {
                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              PutInstanceStorageInstancesByInstanceIdResponse
                                .withPlainInternalServerError(
                                  save.cause().getMessage())));
                        }
                      } catch (Exception e) {
                        asyncResultHandler.handle(
                          Future.succeededFuture(
                            PutInstanceStorageInstancesByInstanceIdResponse
                              .withPlainInternalServerError(e.getMessage())));
                      }
                    });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    PutInstanceStorageInstancesByInstanceIdResponse
                      .withPlainInternalServerError(e.getMessage())));
                }
              }
            } else {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutInstanceStorageInstancesByInstanceIdResponse
                    .withPlainInternalServerError(reply.cause().getMessage())));
            }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutInstanceStorageInstancesByInstanceIdResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        PutInstanceStorageInstancesByInstanceIdResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }

  private void badRequestResult(
    Handler<AsyncResult<Response>> asyncResultHandler, String message) {
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      InstanceStorageResource.GetInstanceStorageInstancesResponse
        .withPlainBadRequest(message)));
  }

  private boolean blankTenantId(String tenantId) {
    return tenantId == null || tenantId == "" || tenantId == "folio_shared";
  }

  private boolean isUUID(String id) {
    try {
      UUID.fromString(id);
      return true;
    }
    catch(IllegalArgumentException e) {
      return false;
    }
  }
}
