package org.folio.services.instance;

import static io.vertx.core.Promise.promise;
import static javax.ws.rs.core.Response.noContent;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.InstanceStorage.DeleteInstanceStorageInstancesByInstanceIdResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorage.DeleteInstanceStorageInstancesResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorage.GetInstanceStorageInstancesByInstanceIdResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorage.PostInstanceStorageInstancesResponse.respond400WithTextPlain;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse.respond201;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse.respond413WithTextPlain;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.support.StatusUpdatedDateGenerator.generateStatusUpdatedDate;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.utils.ComparisonUtils.equalsIgnoringMetadata;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;
import static org.folio.validator.NotesValidators.refuseLongNotes;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.InstanceMarcRepository;
import org.folio.persist.InstanceRelationshipRepository;
import org.folio.persist.InstanceRepository;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
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
  private static final Logger logger = LogManager.getLogger(InstanceService.class);
  private static final String DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING =
    "DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING environment variable doesn't allow to disable optimistic locking";
  private static final String EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY =
    "Expected a maximum of %s records to prevent out of memory but got %s";
  private static final String RESPOND_500_WITH_TEXT_PLAIN = "respond500WithTextPlain";
  private final HridManager hridManager;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final InstanceDomainEventPublisher domainEventPublisher;
  private final InstanceRepository instanceRepository;
  private final InstanceMarcRepository marcRepository;
  private final InstanceRelationshipRepository relationshipRepository;
  private final ConsortiumService consortiumService;

  public InstanceService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(postgresClient);
    domainEventPublisher = new InstanceDomainEventPublisher(vertxContext, okapiHeaders);
    instanceRepository = new InstanceRepository(vertxContext, okapiHeaders);
    marcRepository = new InstanceMarcRepository(vertxContext, okapiHeaders);
    relationshipRepository = new InstanceRelationshipRepository(vertxContext, okapiHeaders);
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

  public Future<Response> getInventoryViewInstancesWithBoundedItems(int offset, int limit, String query) {
    return instanceRepository.getInventoryViewInstancesWithBoundedItems(offset, limit, query);
  }

  public Future<Response> createInstance(Instance entity) {
    entity.setStatusUpdatedDate(generateStatusUpdatedDate());
    return hridManager.populateHrid(entity)
      .compose(NotesValidators::refuseLongNotes)
      .compose(instance -> {
        final Promise<Response> postResponse = promise();

        postgresClient.withTrans(conn ->
          instanceRepository.createInstance(conn, instance)
            .compose(response -> {
              if (response.getEntity() instanceof Instance instanceResp) {
                return batchLinkSubjects(conn, instanceResp.getId(), instance.getSubjects())
                  .map(v -> response);
              } else {
                return Future.succeededFuture(respond400WithTextPlain(response.getEntity()));
              }
            })
            .onSuccess(postResponse::complete)
            .onFailure(throwable -> {
              if (throwable instanceof PgException pgException) {
                postResponse.complete(respond400WithTextPlain(pgException.getDetail()));
              } else {
                postResponse.complete(respond400WithTextPlain(throwable.getMessage()));
              }
            })
        );

        return postResponse.future()
            // Return the response without waiting for a domain event publish
            // to complete. Units of work performed by this service is the same
            // but the ordering of the units of work provides a benefit to the
            // api client invoking this endpoint. The response is returned
            // a little earlier so the api client can continue its processing
            // while the domain event publish is satisfied.
            .onSuccess(domainEventPublisher.publishCreated());
      })
      .map(ResponseHandlerUtil::handleHridErrorInInstance);
  }

  public Future<Response> createInstances(List<Instance> instances, boolean upsert, boolean optimisticLocking,
                                          boolean publishEvents) {
    return createInstances(instances, upsert, optimisticLocking, publishEvents, conn -> Future.succeededFuture());
  }

  public Future<Response> createInstances(List<Instance> instances, boolean upsert, boolean optimisticLocking,
                                          boolean publishEvents, Function<Conn, Future<?>> additionalOperations) {
    final String statusUpdatedDate = generateStatusUpdatedDate();
    instances.forEach(instance -> instance.setStatusUpdatedDate(statusUpdatedDate));

    return hridManager.populateHridForInstances(instances)
      .compose(NotesValidators::refuseInstanceLongNotes)
      .compose(notUsed -> buildBatchOperationContext(upsert, instances,
        instanceRepository, Instance::getId, publishEvents))
      .compose(batchOperation ->
        postgresClient.withTrans(conn ->
            postSyncInstance(conn, instances, upsert, optimisticLocking)
              .compose(response -> batchLinkSubjects(conn, batchOperation.recordsToBeCreated())
                .compose(v -> additionalOperations.apply(conn)).map(response)))
          .onSuccess(domainEventPublisher.publishCreatedOrUpdated(batchOperation)))
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
        try {
          var noChanges = equalsIgnoringMetadata(oldInstance, newInstance);
          if (noChanges) {
            return Future.succeededFuture()
              .map(res -> InstanceStorage.PutInstanceStorageInstancesByInstanceIdResponse.respond204());
          }
        } catch (Exception e) {
          return Future.failedFuture(e);
        }

        final Promise<Response> putResult = promise();
        return postgresClient.withTrans(conn -> {
          Promise<Response> putPromise = putInstance(newInstance, id);
          return putPromise.future()
            .compose(response -> linkOrUnlinkSubjects(conn, newInstance, oldInstance)
              .map(v -> response));
        }).onComplete(transactionResult -> {
          if (transactionResult.succeeded()) {
            putResult.complete(transactionResult.result());
          } else {
            putResult.fail(transactionResult.cause());
          }
        }).onSuccess(domainEventPublisher.publishUpdated(oldInstance));
      });
  }

  private Future<Response> postSyncInstance(Conn conn, List<Instance> instances, boolean upsert,
                                            boolean optimisticLocking) {
    try {
      if (instances != null && instances.size() > MAX_ENTITIES) {
        String message = EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY.formatted(MAX_ENTITIES, instances.size());
        logger.warn("postSyncInstance:: {}", message);
        return Future.succeededFuture(respond413WithTextPlain(message));
      }

      if (optimisticLocking) {
        logger.debug("postSyncInstance:: Unsetting version to -1 for instances");
        OptimisticLockingUtil.unsetVersionIfMinusOne(instances);
      } else {
        if (!OptimisticLockingUtil.isSuppressingOptimisticLockingAllowed()) {
          logger.warn("postSyncInstance:: {}", DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING);
          return Future.succeededFuture(respond413WithTextPlain(DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING));
        }
        logger.debug("postSyncInstance:: Setting version to -1 for instances");
        OptimisticLockingUtil.setVersionToMinusOne(instances);

        MetadataUtil.populateMetadata(instances, okapiHeaders);
      }
      Future<RowSet<Row>> result = upsert
        ? conn.upsertBatch(INSTANCE_TABLE, instances)
        : conn.saveBatch(INSTANCE_TABLE, instances);

      return result
        .map((Response) respond201())
        .recover(throwable ->
          respondFailure(INSTANCE_TABLE, throwable, PostInstanceStorageBatchSynchronousResponse.class)
            .compose(response -> {
              if (response.getEntity() instanceof Errors errors) {
                return Future.failedFuture(new ValidationException(errors));
              }
              return Future.failedFuture(throwable);
            }));
    } catch (Exception e) {
      logger.warn("postSyncInstance:: Error during batch instance", e);
      return Future.failedFuture(e.getMessage());
    }
  }

  private Promise<Response> putInstance(Instance newInstance, String instanceId) {
    Promise<Response> promise = Promise.promise();
    put(INSTANCE_TABLE, newInstance, instanceId, okapiHeaders, vertxContext,
      InstanceStorage.PutInstanceStorageInstancesByInstanceIdResponse.class, reply -> {
        if (reply.succeeded()) {
          promise.complete(reply.result());
        } else {
          promise.fail(reply.cause());
        }
      });
    return promise;
  }

  private Future<Void> linkOrUnlinkSubjects(Conn conn, Instance newInstance, Instance oldInstance) {
    var instanceId = newInstance.getId();

    if (newInstance.getSubjects() == null || newInstance.getSubjects().isEmpty()) {
      return Future.all(
        instanceRepository.unlinkInstanceFromSubjectSource(conn, instanceId),
        instanceRepository.unlinkInstanceFromSubjectType(conn, instanceId)
      ).mapEmpty();
    }
    // Identify the differences between old and new subjects
    Set<Subject> newSubjects = newInstance.getSubjects();
    Set<Subject> oldSubjects = oldInstance.getSubjects() != null ? oldInstance.getSubjects() : Collections.emptySet();

    var subjectsToRemove = oldSubjects.stream()
      .filter(subject -> !newSubjects.contains(subject))
      .collect(Collectors.toSet());

    var subjectsToAdd = newSubjects.stream()
      .filter(subject -> !oldSubjects.contains(subject))
      .collect(Collectors.toSet());

    return batchUnlinkSubjects(conn, instanceId, subjectsToRemove)
      .compose(v -> batchLinkSubjects(conn, instanceId, subjectsToAdd));
  }

  private Future<Void> batchUnlinkSubjects(Conn conn, String instanceId, Set<Subject> subjectsToRemove) {
    if (subjectsToRemove.isEmpty()) {
      return Future.succeededFuture();
    }

    var sourceIds = subjectsToRemove.stream()
      .map(Subject::getSourceId)
      .filter(Objects::nonNull)
      .toList();

    var typeIds = subjectsToRemove.stream()
      .map(Subject::getTypeId)
      .filter(Objects::nonNull)
      .toList();

    return Future.all(
      sourceIds.isEmpty() ? Future.succeededFuture() :
        instanceRepository.batchUnlinkSubjectSource(conn, instanceId, sourceIds),
      typeIds.isEmpty() ? Future.succeededFuture() :
        instanceRepository.batchUnlinkSubjectType(conn, instanceId, typeIds)
    ).mapEmpty();
  }

  private Future<Void> batchLinkSubjects(Conn conn, String instanceId, Set<Subject> subjectsToAdd) {
    var sourcePairs = subjectsToAdd.stream()
      .filter(subject -> subject.getSourceId() != null)
      .map(subject -> Pair.of(instanceId, subject.getSourceId()))
      .toList();

    var typePairs = subjectsToAdd.stream()
      .filter(subject -> subject.getTypeId() != null)
      .map(subject -> Pair.of(instanceId, subject.getTypeId()))
      .toList();

    return batchLinkSubjects(conn, sourcePairs, typePairs);
  }

  private Future<Void> batchLinkSubjects(Conn conn, Collection<Instance> batchInstances) {
    var sourcePairs = batchInstances.stream()
      .flatMap(instance -> instance.getSubjects().stream()
        .filter(subject -> subject.getSourceId() != null)
        .map(subject -> Pair.of(instance.getId(), subject.getSourceId())))
      .toList();

    var typePairs = batchInstances.stream()
      .flatMap(instance -> instance.getSubjects().stream()
        .filter(subject -> subject.getTypeId() != null)
        .map(subject -> Pair.of(instance.getId(), subject.getTypeId())))
      .toList();

    return batchLinkSubjects(conn, sourcePairs, typePairs);
  }

  private Future<Void> batchLinkSubjects(Conn conn, List<Pair<String, String>> sourcePairs,
                                         List<Pair<String, String>> typePairs) {
    if (sourcePairs.isEmpty() && typePairs.isEmpty()) {
      return Future.succeededFuture();
    }

    // Combine both operations into a single future
    return Future.all(
      sourcePairs.isEmpty() ? Future.succeededFuture() :
        instanceRepository.batchLinkSubjectSource(conn, sourcePairs),
      typePairs.isEmpty() ? Future.succeededFuture() :
        instanceRepository.batchLinkSubjectType(conn, typePairs)
    ).mapEmpty();
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
      .map(noContent().build());
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
        return noContent().build();
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
      .map(noContent().build());
  }

  public Future<Void> publishReindexInstanceRecords(String rangeId, String fromId, String toId) {
    return consortiumService.getConsortiumData(okapiHeaders)
      .map(consortiumDataOptional -> consortiumDataOptional
        .map(consortiumData -> isCentralTenantId(okapiHeaders.get(TENANT), consortiumData))
        .orElse(false))
      .compose(isCentralTenant -> {
        var notConsortiumCentralTenant = Boolean.FALSE.equals(isCentralTenant);
        return instanceRepository.getReindexInstances(fromId, toId, notConsortiumCentralTenant);
      })
      .onSuccess(instances -> domainEventPublisher.publishReindexInstances(rangeId, instances))
      .map(notUsed -> null);
  }

  private boolean isCentralTenantId(String tenantId, ConsortiumData consortiumData) {
    return tenantId.equals(consortiumData.centralTenantId());
  }

  private Future<Response> respondFailure(String table, Throwable throwable,
                                          Class<? extends ResponseDelegate> responseClass) {
    try {
      Method respond500 = responseClass.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
      return PgUtil.response(table, "", throwable, responseClass, respond500, respond500);
    } catch (Exception e) {
      logger.debug("respondFailure:: Error during respond", e);
      return Future.failedFuture(e.getMessage());
    }
  }
}
