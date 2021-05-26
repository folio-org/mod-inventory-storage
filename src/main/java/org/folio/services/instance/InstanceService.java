package org.folio.services.instance;

import static io.vertx.core.Promise.promise;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
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
import org.folio.persist.InstanceMarcRepository;
import org.folio.persist.InstanceRelationshipRepository;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.services.domainevent.InstanceDomainEventPublisher;
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
          .compose(domainEventPublisher.publishCreated());
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
          .compose(domainEventPublisher.publishCreatedOrUpdated(batchOperation));
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
          .compose(domainEventPublisher.publishUpdated(oldInstance));
      });
  }

  public Future<Void> deleteAllInstances() {
    return marcRepository.deleteAll()
      .compose(notUsed -> relationshipRepository.deleteAll())
      .compose(notUsed -> instanceRepository.deleteAll())
      .compose(notUsed -> domainEventPublisher.publishAllRemoved());
  }

  public Future<Response> deleteInstance(String id) {
    return instanceRepository.getById(id)
      .compose(CommonValidators::refuseIfNotFound)
      // before deleting the instance record delete its source marc record (foreign key!)
      .compose(instance -> marcRepository.deleteById(id).map(notUsed -> instance))
      .compose(instance -> {
        final Promise<Response> deleteResult = promise();

        deleteById(INSTANCE_TABLE, id, okapiHeaders, vertxContext,
          InstanceStorage.DeleteInstanceStorageInstancesByInstanceIdResponse.class, deleteResult);

        return deleteResult.future()
          .compose(domainEventPublisher.publishRemoved(instance));
      });
  }
}
