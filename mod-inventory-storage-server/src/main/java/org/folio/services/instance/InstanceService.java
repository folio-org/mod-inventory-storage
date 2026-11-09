package org.folio.services.instance;

import static io.vertx.core.Future.failedFuture;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.services.instance.StatusUpdatedDateGenerator.generateStatusUpdatedDate;
import static org.folio.utils.ComparisonUtils.equalsIgnoringMetadata;
import static org.folio.validator.CommonValidators.validateUuidFormat;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;
import static org.folio.validator.NotesValidators.refuseLongNotes;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.InstanceMarcRepository;
import org.folio.persist.InstanceRelationshipRepository;
import org.folio.persist.InstanceRepository;
import org.folio.persist.helper.PgViolationMapper;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.exceptions.RequestEntityTooLargeException;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstancePatchRequest;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClientStreamResult;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.services.CqlQuery;
import org.folio.services.HridManager;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.domainevent.InstanceDomainEventPublisher;
import org.folio.services.reindex.ReindexExportOrchestrator;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.setting.SettingsService;
import org.folio.util.StringUtil;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;
import org.springframework.stereotype.Service;

@Service
public class InstanceService {

  private static final Logger logger = LogManager.getLogger(InstanceService.class);

  private static final String EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY =
    "Expected a maximum of %s records to prevent out of memory but got %s";
  private static final String NOT_FOUND = "Not found";

  private final Context vertxContext;
  private final ConsortiumService consortiumService;
  private final SettingsService settingsService;
  private final Sanitizer<Instance> sanitizer;
  private final InstanceDomainEventPublisher instanceDomainEventPublisher;
  private final ReindexExportOrchestrator reindexExportOrchestrator;
  private final InstanceMarcRepository instanceMarcRepository;
  private final InstanceRelationshipRepository instanceRelationshipRepository;
  private final InstanceRepository instanceRepository;
  private final HridManager hridManager;

  public InstanceService(Context vertxContext, ConsortiumService consortiumService,
                         SettingsService settingsService,
                         InstanceDomainEventPublisher instanceDomainEventPublisher,
                         ReindexExportOrchestrator reindexExportOrchestrator,
                         InstanceMarcRepository instanceMarcRepository,
                         InstanceRelationshipRepository instanceRelationshipRepository,
                         InstanceRepository instanceRepository,
                         HridManager hridManager,
                         Sanitizer<Instance> sanitizer) {
    this.hridManager = hridManager;
    this.instanceRepository = instanceRepository;
    this.instanceRelationshipRepository = instanceRelationshipRepository;
    this.instanceMarcRepository = instanceMarcRepository;
    this.reindexExportOrchestrator = reindexExportOrchestrator;
    this.instanceDomainEventPublisher = instanceDomainEventPublisher;
    this.vertxContext = vertxContext;
    this.consortiumService = consortiumService;
    this.settingsService = settingsService;
    this.sanitizer = sanitizer;
  }

  public Future<Instance> getInstance(String id, Map<String, String> headers) {
    return instanceRepository.getById(id, tenantId(headers))
      .compose(instance -> instance == null
                           ? Future.failedFuture(new NotFoundException(NOT_FOUND))
                           : Future.succeededFuture(instance));
  }

  /**
   * Returns the summary of the instance as JSON.
   */
  public Future<String> getInstanceSummary(String id, Map<String, String> headers) {
    return instanceRepository.getInstanceSummary(id, tenantId(headers))
      .compose(summary -> summary == null
                          ? Future.failedFuture(new NotFoundException(NOT_FOUND))
                          : Future.succeededFuture(summary.encode()));
  }

  public Future<Instance> createInstance(Instance entity, Map<String, String> headers) {
    entity.setStatusUpdatedDate(generateStatusUpdatedDate());
    sanitizer.sanitize(entity);
    return validateUuidFormat(entity.getStatisticalCodeIds())
      .compose(v -> hridManager.populateHrid(entity, tenantId(headers)))
      .compose(NotesValidators::refuseLongNotes)
      .compose(x -> performInstanceCreation(x, headers));
  }

  public Future<Void> createInstances(List<Instance> instances, boolean upsert, boolean optimisticLocking,
                                      boolean publishEvents, Map<String, String> headers) {
    return createInstances(instances, upsert, optimisticLocking, publishEvents, conn -> Future.succeededFuture(),
      headers);
  }

  public Future<Void> createInstances(List<Instance> instances, boolean upsert, boolean optimisticLocking,
                                      boolean publishEvents, Function<Conn, Future<?>> additionalOperations,
                                      Map<String, String> headers) {
    final String statusUpdatedDate = generateStatusUpdatedDate();
    instances.forEach(instance -> {
      instance.setStatusUpdatedDate(statusUpdatedDate);
      sanitizer.sanitize(instance);
    });
    setMissingInstanceIds(instances);

    return hridManager.populateHridForInstances(instances, tenantId(headers))
      .compose(NotesValidators::refuseInstanceLongNotes)
      .compose(notUsed -> buildBatchOperationContext(upsert, instances,
        instanceRepository, Instance::getId, publishEvents, headers))
      .compose(batchOperation ->
        instanceRepository.inTransaction(tenantId(headers), conn ->
            postSyncInstance(conn, instances, upsert, optimisticLocking, headers)
              .compose(v -> batchLinkSubjects(conn, batchOperation.recordsToBeCreated(), headers))
              .compose(v -> additionalOperations.apply(conn)))
          .onSuccess(v -> instanceDomainEventPublisher.publishRecordsCreatedOrUpdated(batchOperation, headers)))
      .mapEmpty();
  }

  public Future<Void> updateInstance(String id, Instance newInstance, Map<String, String> headers) {
    sanitizer.sanitize(newInstance);
    return refuseLongNotes(newInstance)
      .compose(notUsed -> instanceRepository.getById(id, tenantId(headers)))
      .compose(CommonValidators::refuseIfNotFound)
      .compose(oldInstance -> validateHridChange(oldInstance, newInstance))
      .compose(oldInstance -> performInstanceUpdate(id, oldInstance, newInstance, headers));
  }

  public Future<Void> patchInstance(String id, InstancePatchRequest patchRequest,
                                    Map<String, String> headers) {
    String userId = headers.get(XOkapiHeaders.USER_ID);
    var patchJson = JsonObject.mapFrom(patchRequest);
    return instanceRepository.getById(id, tenantId(headers))
      .compose(CommonValidators::refuseIfNotFound)
      .compose(oldInstance ->
        applyPatch(oldInstance, patchJson, userId)
          .compose(newInstance -> validateHridChange(oldInstance, newInstance)
            .compose(ignored -> refuseLongNotes(newInstance))
            .compose(ignored -> performInstanceUpdate(id, oldInstance, newInstance, headers))));
  }

  /**
   * Deletes all instances but sends only a single domain event (Kafka) message "all records removed",
   * this is much faster than sending one message for each deleted instance.
   */
  public Future<Void> deleteAllInstances(Map<String, String> headers) {
    return instanceMarcRepository.deleteAll(tenantId(headers))
      .compose(notUsed -> instanceRelationshipRepository.deleteAll(tenantId(headers)))
      .compose(notUsed -> instanceRepository.deleteAll(tenantId(headers)))
      .onSuccess(notUsed -> instanceDomainEventPublisher.publishAllRemoved(headers))
      .mapEmpty();
  }

  public Future<PostgresClientStreamResult<Instance>> streamInstances(String query, int offset, int limit,
                                                                      Map<String, String> headers) {
    return instanceRepository.stream(query, offset, limit, tenantId(headers));
  }

  public Future<Void> deleteInstance(String id, Map<String, String> headers) {
    return instanceRepository.delete("id==" + StringUtil.cqlEncode(id), tenantId(headers))
      .compose(deleted -> {
        if (deleted.isEmpty()) {
          return Future.failedFuture(new NotFoundException(NOT_FOUND));
        }
        deleted.forEach(deletedRecord ->
          instanceDomainEventPublisher
            .publishRemoved(deletedRecord.instanceId(), deletedRecord.json(), headers)
        );
        return Future.succeededFuture();
      });
  }

  /**
   * Delete instances, this also deletes connected marc records (ON DELETE CASCADE).
   */
  public Future<Void> deleteInstances(String cql, Map<String, String> headers) {
    if (StringUtils.isBlank(cql)) {
      return Future.failedFuture(new BadRequestException("Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAllInstances(headers);  // faster: sends only one domain event (Kafka) message
    }
    return instanceRepository.delete(cql, tenantId(headers))
      .onSuccess(deleted -> vertxContext.runOnContext(runLater ->
        deleted.forEach(deletedRecord ->
          instanceDomainEventPublisher
            .publishRemoved(deletedRecord.instanceId(), deletedRecord.json(), headers)
        )
      ))
      .mapEmpty();
  }

  public Future<Void> publishReindexInstanceRecords(String rangeId, String fromId, String toId,
                                                    Map<String, String> headers) {
    return isCentralTenant(headers)
      .compose(isCentralTenant -> {
        var notConsortiumCentralTenant = Boolean.FALSE.equals(isCentralTenant);
        return instanceRepository
          .getReindexInstances(fromId, toId, notConsortiumCentralTenant, tenantId(headers));
      })
      .onSuccess(instances -> instanceDomainEventPublisher
        .publishReindexInstances(rangeId, instances, headers))
      .map(notUsed -> null);
  }

  public Future<Void> exportReindexInstanceRecords(ReindexRecordsRequest request, Map<String, String> headers) {
    var rangeFrom = request.getRecordIdsRange().getFrom();
    var rangeTo = request.getRecordIdsRange().getTo();

    return isCentralTenant(headers)
      .compose(isCentralTenant -> {
        var notConsortiumCentralTenant = Boolean.FALSE.equals(isCentralTenant);
        return reindexExportOrchestrator.export(request, headers,
          instanceRepository,
          conn -> instanceRepository
            .streamReindexInstances(conn, rangeFrom, rangeTo, notConsortiumCentralTenant, tenantId(headers)));
      });
  }

  private Future<Instance> performInstanceCreation(Instance instance, Map<String, String> headers) {
    return instanceRepository.inTransaction(tenantId(headers), conn ->
        instanceRepository.createInstance(conn, instance)
          .compose(created -> batchLinkSubjects(conn, created.getId(), instance.getSubjects(), headers)
            .map(v -> created)))
      .recover(throwable -> Future.failedFuture(instanceCreationFailure(throwable)))
      // Return without waiting for a domain event publish to complete. Units of work performed by this
      // service is the same but the ordering of the units of work provides a benefit to the
      // api client invoking this endpoint. The response is returned
      // a little earlier so the api client can continue its processing
      // while the domain event publish is satisfied.
      .onSuccess(created -> instanceDomainEventPublisher.publishRecordCreated(created, headers));
  }

  private Throwable instanceCreationFailure(Throwable throwable) {
    if (throwable instanceof PgException pgException) {
      return new BadRequestException(pgException.getDetail() != null
        ? pgException.getDetail()
        : pgException.getMessage());
    }
    return new BadRequestException(throwable.getMessage());
  }

  private Future<Instance> applyPatch(Instance instance, JsonObject patchJson, String userId) {
    var metadata = instance.getMetadata();
    if (metadata != null) {
      metadata.setUpdatedDate(new Date());
      metadata.setUpdatedByUserId(userId);
    }
    var instanceJson = JsonObject.mapFrom(instance);
    var patchedJson = instanceJson.mergeIn(patchJson);
    try {
      return Future.succeededFuture(patchedJson.mapTo(Instance.class));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private Future<Instance> validateHridChange(Instance oldInstance, Instance newInstance) {
    if (!newInstance.getSource().startsWith("CONSORTIUM-")) {
      return refuseWhenHridChanged(oldInstance, newInstance);
    }
    return Future.succeededFuture(oldInstance);
  }

  private Future<Void> performInstanceUpdate(String id, Instance oldInstance, Instance newInstance,
                                             Map<String, String> headers) {
    return settingsService.isOptimizeUpdatesEnabled(headers)
      .compose(isOptimizeUpdatesEnabled -> {
        try {
          if (Boolean.TRUE.equals(isOptimizeUpdatesEnabled) && equalsIgnoringMetadata(oldInstance, newInstance)) {
            return Future.<Void>succeededFuture();
          }
        } catch (Exception e) {
          return failedFuture(e);
        }
        return instanceRepository.inTransaction(tenantId(headers), conn ->
            instanceRepository.replace(id, newInstance, tenantId(headers))
              .recover(InstanceService::databaseViolationAsBadRequest)
              .compose(v -> linkOrUnlinkSubjects(conn, newInstance, oldInstance, headers)))
          .onSuccess(v -> instanceDomainEventPublisher.publishRecordUpdated(oldInstance, headers));
      });
  }

  /**
   * The instance PUT does not declare 422, so constraint violations of the database are reported as 400.
   */
  private static Future<Void> databaseViolationAsBadRequest(Throwable failure) {
    if (failure instanceof ValidationException validationException) {
      var message = validationException.getErrors().getErrors().getFirst().getMessage();
      return failedFuture(new BadRequestException(message));
    }
    return failedFuture(failure);
  }

  private Future<Void> postSyncInstance(Conn conn, List<Instance> instances, boolean upsert,
                                        boolean optimisticLocking, Map<String, String> headers) {
    try {
      var sizeFailure = validateInstancesSize(instances);
      if (sizeFailure != null) {
        return Future.failedFuture(sizeFailure);
      }

      instanceRepository.applyOptimisticLocking(instances, optimisticLocking);

      MetadataUtil.populateMetadata(instances, headers);
      return executeBatchOperation(conn, instances, upsert);
    } catch (RequestEntityTooLargeException e) {
      logger.warn("postSyncInstance:: {}", e.getMessage());
      return Future.failedFuture(e);
    } catch (Exception e) {
      logger.warn("postSyncInstance:: Error during batch instance", e);
      return failedFuture(e.getMessage());
    }
  }

  private Future<Void> executeBatchOperation(Conn conn, List<Instance> instances, boolean upsert) {
    var result = upsert
                 ? conn.upsertBatch(INSTANCE_TABLE, instances)
                 : conn.saveBatch(INSTANCE_TABLE, instances);

    return result
      .<Void>mapEmpty()
      .recover(throwable -> failedFuture(PgViolationMapper.map(INSTANCE_TABLE, "", throwable)));
  }

  private Throwable validateInstancesSize(List<Instance> instances) {
    if (instances != null && instances.size() > MAX_ENTITIES) {
      String message = EXPECTED_A_MAXIMUM_RECORDS_TO_PREVENT_OUT_OF_MEMORY.formatted(MAX_ENTITIES, instances.size());
      logger.warn("validateInstancesSize:: {}", message);
      return new RequestEntityTooLargeException(message);
    }
    return null;
  }

  private void setMissingInstanceIds(List<Instance> instances) {
    if (instances == null) {
      return;
    }

    instances.forEach(instance -> instance.setId(instance.getId() == null
                                                 ? UUID.randomUUID().toString()
                                                 : instance.getId()));
  }

  private Future<Void> linkOrUnlinkSubjects(Conn conn, Instance newInstance, Instance oldInstance,
                                            Map<String, String> headers) {
    var instanceId = newInstance.getId();

    if (newInstance.getSubjects() == null || newInstance.getSubjects().isEmpty()) {
      return Future.all(
        instanceRepository.unlinkInstanceFromSubjectSource(conn, instanceId, tenantId(headers)),
        instanceRepository.unlinkInstanceFromSubjectType(conn, instanceId, tenantId(headers))
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

    return batchUnlinkSubjects(conn, instanceId, subjectsToRemove, headers)
      .compose(v -> batchLinkSubjects(conn, instanceId, subjectsToAdd, headers));
  }

  private Future<Void> batchUnlinkSubjects(Conn conn, String instanceId, Set<Subject> subjectsToRemove,
                                           Map<String, String> headers) {
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
      instanceRepository.batchUnlinkSubjectSource(conn, instanceId, sourceIds, tenantId(headers)),
      typeIds.isEmpty() ? Future.succeededFuture() :
      instanceRepository.batchUnlinkSubjectType(conn, instanceId, typeIds, tenantId(headers))
    ).mapEmpty();
  }

  private Future<Void> batchLinkSubjects(Conn conn, String instanceId, Set<Subject> subjectsToAdd,
                                         Map<String, String> headers) {
    var sourcePairs = subjectsToAdd.stream()
      .filter(subject -> subject.getSourceId() != null)
      .map(subject -> Pair.of(instanceId, subject.getSourceId()))
      .toList();

    var typePairs = subjectsToAdd.stream()
      .filter(subject -> subject.getTypeId() != null)
      .map(subject -> Pair.of(instanceId, subject.getTypeId()))
      .toList();

    return batchLinkSubjects(conn, sourcePairs, typePairs, headers);
  }

  private Future<Void> batchLinkSubjects(Conn conn, Collection<Instance> batchInstances,
                                         Map<String, String> headers) {
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

    return batchLinkSubjects(conn, sourcePairs, typePairs, headers);
  }

  private Future<Void> batchLinkSubjects(Conn conn, List<Pair<String, String>> sourcePairs,
                                         List<Pair<String, String>> typePairs, Map<String, String> headers) {
    if (sourcePairs.isEmpty() && typePairs.isEmpty()) {
      return Future.succeededFuture();
    }

    // Combine both operations into a single future
    return Future.all(
      sourcePairs.isEmpty() ? Future.succeededFuture() :
      instanceRepository.batchLinkSubjectSource(conn, sourcePairs, tenantId(headers)),
      typePairs.isEmpty() ? Future.succeededFuture() :
      instanceRepository.batchLinkSubjectType(conn, typePairs, tenantId(headers))
    ).mapEmpty();
  }

  private Future<Boolean> isCentralTenant(Map<String, String> headers) {
    return consortiumService.getConsortiumData(headers)
      .map(consortiumDataOptional -> consortiumDataOptional
        .map(consortiumData -> isCentralTenantId(headers.get(TENANT), consortiumData))
        .orElse(false));
  }

  private boolean isCentralTenantId(String tenantId, ConsortiumData consortiumData) {
    return tenantId.equals(consortiumData.centralTenantId());
  }
}
