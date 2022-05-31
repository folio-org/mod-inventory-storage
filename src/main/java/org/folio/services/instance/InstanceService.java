package org.folio.services.instance;

import static io.vertx.core.Promise.promise;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.InstanceStorage.DeleteInstanceStorageInstancesByInstanceIdResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorage.DeleteInstanceStorageInstancesResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.support.StatusUpdatedDateGenerator.generateStatusUpdatedDate;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.persist.InstanceMarcRepository;
import org.folio.persist.InstanceRelationshipRepository;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.CqlUtil;
import org.folio.rest.support.HridManager;
import org.folio.services.domainevent.InstanceDomainEventPublisher;
import org.folio.util.StringUtil;
import org.folio.validator.CommonValidators;

public class InstanceService {
  private final HridManager hridManager;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final InstanceDomainEventPublisher domainEventPublisher;
  private final InstanceRepository instanceRepository;
  private final InstanceMarcRepository marcRepository;
  private final InstanceRelationshipRepository relationshipRepository;
  private final InstanceEffectiveValuesService effectiveValuesService;

  public InstanceService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    final PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(vertxContext, postgresClient);
    domainEventPublisher = new InstanceDomainEventPublisher(vertxContext, okapiHeaders);
    instanceRepository = new InstanceRepository(vertxContext, okapiHeaders);
    marcRepository = new InstanceMarcRepository(vertxContext, okapiHeaders);
    relationshipRepository = new InstanceRelationshipRepository(vertxContext, okapiHeaders);
    effectiveValuesService = new InstanceEffectiveValuesService();
  }

  public Future<Response> createInstance(Instance entity) {
    entity.setStatusUpdatedDate(generateStatusUpdatedDate());
    effectiveValuesService.populateEffectiveValues(entity);

    return hridManager.populateHrid(entity)
      .compose(instance -> {
        final Promise<Response> postResponse = promise();

        post(INSTANCE_TABLE, instance, okapiHeaders, vertxContext,
          InstanceStorage.PostInstanceStorageInstancesResponse.class, postResponse);

        return postResponse.future()
            // Return the response without waiting for a domain event publish
            // to complete. Units of work performed by this service is the same
            // but the ordering of the units of work provides a benefit to the
            // api client invoking this endpoint. The response is returned
            // a little earlier so the api client can continue its processing
            // while the domain event publish is satisfied.
            .onSuccess(domainEventPublisher.publishCreated());
      });
  }

  public Future<Response> createInstances(List<Instance> instances, boolean upsert) {
    final String statusUpdatedDate = generateStatusUpdatedDate();
    instances.forEach(instance -> instance.setStatusUpdatedDate(statusUpdatedDate));

    return hridManager.populateHridForInstances(instances)
      .compose(notUsed -> buildBatchOperationContext(upsert, instances,
        instanceRepository, Instance::getId))
      .compose(batchOperation -> {
        final Promise<Response> postResult = promise();

        effectiveValuesService.populateEffectiveValues(instances, batchOperation);
        // Can use instances list here directly because the class is stateful
        postSync(INSTANCE_TABLE, instances, MAX_ENTITIES, upsert, okapiHeaders,
          vertxContext, PostInstanceStorageBatchSynchronousResponse.class, postResult);

        return postResult.future()
          .onSuccess(domainEventPublisher.publishCreatedOrUpdated(batchOperation));
      });
  }

  public Future<Response> updateInstance(String id, Instance newInstance) {
    return instanceRepository.getById(id)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(oldInstance -> refuseWhenHridChanged(oldInstance, newInstance))
      .compose(oldInstance -> {
        final Promise<Response> putResult = promise();

        effectiveValuesService.populateEffectiveValues(newInstance, oldInstance);
        put(INSTANCE_TABLE, newInstance, id, okapiHeaders, vertxContext,
          InstanceStorage.PutInstanceStorageInstancesByInstanceIdResponse.class, putResult);

        return putResult.future()
          .onSuccess(domainEventPublisher.publishUpdated(oldInstance));
      });
  }

  public Future<Response> deleteAllInstances() {
    return marcRepository.deleteAll()
      .compose(notUsed -> relationshipRepository.deleteAll())
      .compose(notUsed -> instanceRepository.deleteAll())
      .onSuccess(notUsed -> domainEventPublisher.publishAllRemoved())
      .map(Response.noContent().build());
  }

  /**
   * Delete instance and connected marc record.
   */
  // suppress "Remove useless curly braces around lambda containing only one statement"
  @SuppressWarnings("java:S1602")
  public Future<Response> deleteInstance(String id) {
    return instanceRepository.delete("id==" + StringUtil.cqlEncode(id))
        .map(rowSet -> {
          if (! rowSet.iterator().hasNext()) {
            return DeleteInstanceStorageInstancesByInstanceIdResponse.respond404WithTextPlain("Not found");
          }
          rowSet.iterator().forEachRemaining(row -> {
            domainEventPublisher.publishRemoved(row.getString(0), row.getString(1));
          });
          return Response.noContent().build();
        });
  }

  /**
   * Delete instances and connected marc records.
   */
  // suppress "Remove useless curly braces around lambda containing only one statement"
  @SuppressWarnings("java:S1602")
  public Future<Response> deleteInstances(String cql) {
    if (StringUtils.isBlank(cql)) {
      return Future.succeededFuture(
          DeleteInstanceStorageInstancesResponse.respond400WithTextPlain(
              "Expected CQL but query parameter is empty"));
    }
    if (CqlUtil.isMatchingAll(cql)) {
      return deleteAllInstances();  // faster: sends only one domain event (Kafka) message
    }
    return instanceRepository.delete(cql)
        .onSuccess(rowSet -> vertxContext.runOnContext(runLater -> {
          rowSet.iterator().forEachRemaining(row -> {
            domainEventPublisher.publishRemoved(row.getString(0), row.getString(1));
          });
        }))
        .map(Response.noContent().build());
  }

}
