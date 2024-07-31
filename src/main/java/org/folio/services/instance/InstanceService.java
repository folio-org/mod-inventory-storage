package org.folio.services.instance;

import static io.vertx.core.Promise.promise;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.InstanceStorage.DeleteInstanceStorageInstancesByInstanceIdResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorage.DeleteInstanceStorageInstancesResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorage.GetInstanceStorageInstancesByInstanceIdResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.support.StatusUpdatedDateGenerator.generateStatusUpdatedDate;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;
import static org.folio.validator.NotesValidators.refuseLongNotes;

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
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.HridManager;
import org.folio.services.ResponseHandlerUtil;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.ConsortiumServiceImpl;
import org.folio.services.domainevent.InstanceDomainEventPublisher;
import org.folio.util.StringUtil;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;

public class InstanceService {
  private final HridManager hridManager;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final InstanceDomainEventPublisher domainEventPublisher;
  private final InstanceRepository instanceRepository;
  private final InstanceMarcRepository marcRepository;
  private final InstanceRelationshipRepository relationshipRepository;
  private final InstanceEffectiveValuesService effectiveValuesService;
  private final ConsortiumService consortiumService;

  public InstanceService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    final PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(postgresClient);
    domainEventPublisher = new InstanceDomainEventPublisher(vertxContext, okapiHeaders);
    instanceRepository = new InstanceRepository(vertxContext, okapiHeaders);
    marcRepository = new InstanceMarcRepository(vertxContext, okapiHeaders);
    relationshipRepository = new InstanceRelationshipRepository(vertxContext, okapiHeaders);
    effectiveValuesService = new InstanceEffectiveValuesService();
    consortiumService = new ConsortiumServiceImpl(vertxContext.owner().createHttpClient(),
      vertxContext.get(ConsortiumDataCache.class.getName()));
  }

  public Future<Response> getInstance(String id) {
    return instanceRepository.getById(id)
      .map(instance -> {
        if (instance == null) {
          return GetInstanceStorageInstancesByInstanceIdResponse.respond404WithTextPlain(null);
        }
        return GetInstanceStorageInstancesByInstanceIdResponse.respond200WithApplicationJson(instance);
      });
  }

  @SuppressWarnings("java:S107")
  // suppress "Methods should not have too many parameters"
  public Future<Response> getInstanceSet(boolean instance, boolean holdingsRecords, boolean items,
                                         boolean precedingTitles, boolean succeedingTitles,
                                         boolean superInstanceRelationships, boolean subInstanceRelationships,
                                         int offset, int limit, String query) {

    return instanceRepository.getInstanceSet(instance, holdingsRecords, items,
      precedingTitles, succeedingTitles, superInstanceRelationships, subInstanceRelationships,
      offset, limit, query);
  }

  public Future<Response> createInstance(Instance entity) {
    entity.setStatusUpdatedDate(generateStatusUpdatedDate());
    effectiveValuesService.populateEffectiveValues(entity);

    return hridManager.populateHrid(entity)
      .compose(NotesValidators::refuseLongNotes)
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
      })
      .map(ResponseHandlerUtil::handleHridError);
  }

  public Future<Response> createInstances(List<Instance> instances, boolean upsert, boolean optimisticLocking) {
    final String statusUpdatedDate = generateStatusUpdatedDate();
    instances.forEach(instance -> instance.setStatusUpdatedDate(statusUpdatedDate));

    return hridManager.populateHridForInstances(instances)
      .compose(NotesValidators::refuseInstanceLongNotes)
      .compose(notUsed -> buildBatchOperationContext(upsert, instances,
        instanceRepository, Instance::getId))
      .compose(batchOperation -> {
        effectiveValuesService.populateEffectiveValues(instances, batchOperation);
        // Can use instances list here directly because the class is stateful
        return postSync(INSTANCE_TABLE, instances, MAX_ENTITIES, upsert, optimisticLocking, okapiHeaders,
          vertxContext, PostInstanceStorageBatchSynchronousResponse.class)
          .onSuccess(domainEventPublisher.publishCreatedOrUpdated(batchOperation));
      })
      .map(ResponseHandlerUtil::handleHridError);
  }

  public Future<Response> updateInstance(String id, Instance newInstance) {
    return refuseLongNotes(newInstance)
      .compose(notUsed -> instanceRepository.getById(id))
      .compose(CommonValidators::refuseIfNotFound)
      .compose(oldInstance -> {
        if (!newInstance.getSource().startsWith("CONSORTIUM-")) {
          return refuseWhenHridChanged(oldInstance, newInstance);
        }
        return Future.succeededFuture(oldInstance);
      })
      .compose(oldInstance -> {
        final Promise<Response> putResult = promise();

        effectiveValuesService.populateEffectiveValues(newInstance, oldInstance);
        put(INSTANCE_TABLE, newInstance, id, okapiHeaders, vertxContext,
          InstanceStorage.PutInstanceStorageInstancesByInstanceIdResponse.class, putResult);

        return putResult.future()
          .onSuccess(domainEventPublisher.publishUpdated(oldInstance));
      });
  }

  /**
   * Deletes all instances but sends only a single domain event (Kafka) message "all records removed",
   * this is much faster than sending one message for each deleted instance.
   */
  public Future<Response> deleteAllInstances() {
    return marcRepository.deleteAll()
      .compose(notUsed -> relationshipRepository.deleteAll())
      .compose(notUsed -> instanceRepository.deleteAll())
      .onSuccess(notUsed -> domainEventPublisher.publishAllRemoved())
      .map(Response.noContent().build());
  }

  /**
   * Delete instance, this also deletes connected marc records (ON DELETE CASCADE).
   */
  public Future<Response> deleteInstance(String id) {
    return instanceRepository.delete("id==" + StringUtil.cqlEncode(id))
      .map(rowSet -> {
        if (!rowSet.iterator().hasNext()) {
          return DeleteInstanceStorageInstancesByInstanceIdResponse.respond404WithTextPlain("Not found");
        }
        // do not add curly braces for readability, this is to comply with
        // https://sonarcloud.io/organizations/folio-org/rules?open=java%3AS1602&rule_key=java%3AS1602
        rowSet.iterator().forEachRemaining(row ->
          domainEventPublisher.publishRemoved(row.getString(0), row.getString(1))
        );
        return Response.noContent().build();
      });
  }

  /**
   * Delete instances, this also deletes connected marc records (ON DELETE CASCADE).
   */
  public Future<Response> deleteInstances(String cql) {
    if (StringUtils.isBlank(cql)) {
      return Future.succeededFuture(
        DeleteInstanceStorageInstancesResponse.respond400WithTextPlain(
          "Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAllInstances();  // faster: sends only one domain event (Kafka) message
    }
    // do not add curly braces for readability, this is to comply with
    // https://sonarcloud.io/organizations/folio-org/rules?open=java%3AS1602&rule_key=java%3AS1602
    return instanceRepository.delete(cql)
      .onSuccess(rowSet -> vertxContext.runOnContext(runLater ->
        rowSet.iterator().forEachRemaining(row ->
          domainEventPublisher.publishRemoved(row.getString(0), row.getString(1))
        )
      ))
      .map(Response.noContent().build());
  }

  public Future<Void> publishReindexInstanceRecords(String rangeId, String fromId, String toId) {
    var criteriaFrom = new Criteria().setJSONB(false)
      .addField("id").setOperation(">=").setVal(fromId);
    var criteriaTo = new Criteria().setJSONB(false)
      .addField("id").setOperation("<=").setVal(toId);
    final Criterion criterion = new Criterion(criteriaFrom)
      .addCriterion(criteriaTo);

    return consortiumService.getConsortiumData(okapiHeaders)
      .map(consortiumDataOptional -> consortiumDataOptional
        .map(consortiumData -> isCentralTenantId(okapiHeaders.get(TENANT), consortiumData))
        .orElse(false))
      .compose(isCentralTenant -> {
        if (Boolean.TRUE.equals(isCentralTenant)) {
          return instanceRepository.get(criterion);
        }
        var nonConsortia = new Criteria()
          .addField("source").setOperation("NOT LIKE").setVal("CONSORTIUM-");
        criterion.addCriterion(nonConsortia);
        return instanceRepository.get(criterion);
      })
      .compose(instances -> domainEventPublisher.publishReindexInstances(rangeId, instances));
  }

  private boolean isCentralTenantId(String tenantId, ConsortiumData consortiumData) {
    return tenantId.equals(consortiumData.getCentralTenantId());
  }

}
