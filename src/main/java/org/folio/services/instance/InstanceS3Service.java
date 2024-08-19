package org.folio.services.instance;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.rest.support.InstanceUtil.mapInstanceDtoJsonToInstance;
import static org.folio.rest.support.ResponseUtil.isCreateSuccessResponse;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.ServerErrorException;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceBulkRequest;
import org.folio.rest.jaxrs.model.InstanceBulkResponse;
import org.folio.rest.jaxrs.model.PrecedingSucceedingTitle;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.BulkProcessingErrorFileWriter;
import org.folio.rest.support.InstanceUtil;
import org.folio.s3.client.FolioS3Client;
import org.folio.services.BulkProcessingContext;
import org.folio.services.s3storage.FolioS3ClientFactory;

/**
 * The service that interacts with S3-compatible storage to perform upsert operations on instances retrieved
 * from external file. The file to be processed is specified through {@link InstanceBulkRequest}.
 */
public class InstanceS3Service {

  private static final Logger log = LogManager.getLogger(InstanceS3Service.class);
  private static final String PRECEDING_SUCCEEDING_TITLES_CQL_BUILD_ERROR_MSG =
    "buildPrecedingSucceedingTitlesCql:: Failed to build CQL wrapper for preceding and succeeding titles search";
  private static final String INSTANCES_PARALLEL_UPSERT_COUNT_PARAM = "bulk-processing.parallel.upsert.count";
  private static final String DEFAULT_INSTANCES_PARALLEL_UPSERT_COUNT = "10";
  private static final String PRECEDING_SUCCEEDING_TITLES_BY_INSTANCE_IDS_CQL =
    "succeedingInstanceId==(%1$s) or precedingInstanceId==(%1$s)";
  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";
  private static final String PRECEDING_TITLES_FIELD = "precedingTitles";
  private static final String SUCCEEDING_TITLES_FIELD = "succeedingTitles";
  private static final String OR_OPERATOR = " or ";
  private static final String ID_FIELD = "id";
  private static final boolean APPLY_UPSERT = true;
  private static final boolean APPLY_OPTIMISTIC_LOCKING = true;

  private final FolioS3ClientFactory folioS3ClientFactory;
  private final Vertx vertx;
  private final FolioS3Client folioS3Client;
  private final InstanceService instanceService;
  private final PostgresClient postgresClient;
  private final InstanceRepository instanceRepository;
  private final int instancesParallelUpsertLimit;

  public InstanceS3Service(FolioS3ClientFactory folioS3ClientFactory, Vertx vertx, Map<String, String> okapiHeaders) {
    this.instancesParallelUpsertLimit = Integer.parseInt(
      System.getProperty(INSTANCES_PARALLEL_UPSERT_COUNT_PARAM, DEFAULT_INSTANCES_PARALLEL_UPSERT_COUNT));
    this.vertx = vertx;
    this.folioS3ClientFactory = folioS3ClientFactory;
    this.folioS3Client = folioS3ClientFactory.getFolioS3Client();
    this.instanceService = new InstanceService(vertx.getOrCreateContext(), okapiHeaders);
    this.instanceRepository = new InstanceRepository(vertx.getOrCreateContext(), okapiHeaders);
    this.postgresClient = PgUtil.postgresClient(vertx.getOrCreateContext(), okapiHeaders);
  }

  /**
   * Processes a bulk request for instances by loading instances from the specified file in {@link InstanceBulkRequest}
   * located on S3-compatible storage, and upserts them into the database.
   * If an errors occurs during the processing, the method uploads two files containing the failed instances
   * and their associated errors to S3-compatible storage.
   *
   * @param bulkRequest - bulk instances request containing external file to be processed
   * @return {@link Future} of {@link InstanceBulkResponse} containing errors count, and files with failed instances
   *   and errors encountered during processing
   */
  public Future<InstanceBulkResponse> processInstances(InstanceBulkRequest bulkRequest) {
    log.debug("processInstances:: Processing bulk instances request, filename: '{}'", bulkRequest.getRecordsFileName());
    return loadInstances(bulkRequest)
      .compose(instances -> upsert(instances, bulkRequest))
      .onFailure(e -> log.warn("processInstances:: Failed to process instances bulk request, filename: '{}'",
        bulkRequest.getRecordsFileName(), e));
  }

  private Future<List<JsonObject>> loadInstances(InstanceBulkRequest bulkRequest) {
    return vertx.executeBlocking(() -> {
      FolioS3Client s3Client = folioS3ClientFactory.getFolioS3Client();
      InputStream inputStream = s3Client.read(bulkRequest.getRecordsFileName());

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        return reader.lines()
          .map(JsonObject::new)
          .toList();
      }
    });
  }

  private Future<InstanceBulkResponse> upsert(List<JsonObject> instances, InstanceBulkRequest bulkRequest) {
    BulkProcessingContext bulkProcessingContext = new BulkProcessingContext(bulkRequest.getRecordsFileName());
    List<Pair<Instance, List<PrecedingSucceedingTitle>>> instancesPairs = instances.stream()
      .map(json -> Pair.of(mapInstanceDtoJsonToInstance(json), extractPrecedingSucceedingTitles(json)))
      .toList();

    return upsert(instancesPairs)
      .map(v -> new InstanceBulkResponse().withErrorsNumber(0))
      .recover(e -> processSequentially(instancesPairs, bulkProcessingContext));
  }

  private Future<Void> upsert(List<Pair<Instance, List<PrecedingSucceedingTitle>>> instances) {
    List<Instance> instanceList = instances.stream().map(Pair::getLeft).toList();

    return ensureInstancesWithNonMarcControlledFields(instanceList)
      .compose(v -> instanceService.createInstances(instanceList, APPLY_UPSERT, APPLY_OPTIMISTIC_LOCKING))
      .compose(response -> {
        if (!isCreateSuccessResponse(response)) {
          String msg = String.format("Failed to update instances, status: '%s', message: '%s'",
            response.getStatus(), Json.encode(response.getEntity()));
          return Future.failedFuture(msg);
        }
        return Future.succeededFuture();
      })
      .compose(v -> updatePrecedingSucceedingTitles(instances));
  }

  private List<PrecedingSucceedingTitle> extractPrecedingSucceedingTitles(JsonObject instanceJson) {
    List<PrecedingSucceedingTitle> titles = new ArrayList<>();
    instanceJson.getJsonArray(PRECEDING_TITLES_FIELD).stream()
      .map(JsonObject.class::cast)
      .map(title -> title.mapTo(PrecedingSucceedingTitle.class))
      .map(title -> title.withSucceedingInstanceId(instanceJson.getString(ID_FIELD)))
      .forEach(titles::add);

    instanceJson.getJsonArray(SUCCEEDING_TITLES_FIELD).stream()
      .map(JsonObject.class::cast)
      .map(title -> title.mapTo(PrecedingSucceedingTitle.class))
      .map(title -> title.withPrecedingInstanceId(instanceJson.getString(ID_FIELD)))
      .forEach(titles::add);
    return titles;
  }

  private Future<Void> ensureInstancesWithNonMarcControlledFields(List<Instance> instances) {
    return instanceRepository.getById(instances, Instance::getId).compose(existingInstances -> {
      instances.forEach(instance -> {
        if (existingInstances.get(instance.getId()) != null) {
          InstanceUtil.copyNonMarcControlledFields(instance, existingInstances.get(instance.getId()));
        }
      });
      return Future.succeededFuture();
    });
  }

  private Future<Void> updatePrecedingSucceedingTitles(List<Pair<Instance, List<PrecedingSucceedingTitle>>> instances) {
    List<PrecedingSucceedingTitle> precedingSucceedingTitles = instances.stream()
      .map(Pair::getRight)
      .flatMap(Collection::stream)
      .toList();

    if (precedingSucceedingTitles.isEmpty()) {
      return Future.succeededFuture();
    }

    CQLWrapper cqlQuery = buildPrecedingSucceedingTitlesCql(instances);
    return postgresClient.delete(PRECEDING_SUCCEEDING_TITLE_TABLE, cqlQuery)
      .compose(v -> postgresClient.saveBatch(PRECEDING_SUCCEEDING_TITLE_TABLE, precedingSucceedingTitles))
      .mapEmpty();
  }

  private CQLWrapper buildPrecedingSucceedingTitlesCql(List<Pair<Instance, List<PrecedingSucceedingTitle>>> instances) {
    try {
      String idsValue = instances.stream()
        .map(Pair::getLeft)
        .map(Instance::getId)
        .collect(Collectors.joining(OR_OPERATOR));

      String cql = String.format(PRECEDING_SUCCEEDING_TITLES_BY_INSTANCE_IDS_CQL, idsValue);
      return new CQLWrapper(new CQL2PgJSON(PRECEDING_SUCCEEDING_TITLE_TABLE + ".jsonb"), cql);
    } catch (FieldException e) {
      log.warn(PRECEDING_SUCCEEDING_TITLES_CQL_BUILD_ERROR_MSG, e);
      throw new ServerErrorException(e.getMessage(), INTERNAL_SERVER_ERROR);
    }
  }

  private Future<InstanceBulkResponse> processSequentially(
    List<Pair<Instance, List<PrecedingSucceedingTitle>>> instances, BulkProcessingContext bulkContext) {
    AtomicInteger errorsCounter = new AtomicInteger();
    BulkProcessingErrorFileWriter errorsWriter = new BulkProcessingErrorFileWriter(vertx, bulkContext);

    return errorsWriter.initialize()
      .compose(v -> processInBatches(instances, instancePair -> upsert(List.of(instancePair))
        .recover(e -> handleInstanceUpsertFailure(errorsCounter, errorsWriter, instancePair.getLeft(), e))))
      .eventually(errorsWriter::close)
      .eventually(() -> uploadErrorsFiles(bulkContext))
      .transform(ar -> Future.succeededFuture(new InstanceBulkResponse()
        .withErrorsNumber(errorsCounter.get())
        .withErrorRecordsFileName(bulkContext.getErrorEntitiesFilePath())
        .withErrorsFileName(bulkContext.getErrorsFilePath())
      ));
  }

  private Future<Void> processInBatches(List<Pair<Instance, List<PrecedingSucceedingTitle>>> instances,
                                        Function<Pair<Instance, List<PrecedingSucceedingTitle>>, Future<Void>> task) {
    Future<Void> future = Future.succeededFuture();
    List<List<Pair<Instance, List<PrecedingSucceedingTitle>>>> instancesBatches =
      ListUtils.partition(instances, instancesParallelUpsertLimit);

    for (List<Pair<Instance, List<PrecedingSucceedingTitle>>> batch : instancesBatches) {
      future = future.eventually(() -> processBatch(batch, task));
    }
    return future;
  }

  private CompositeFuture processBatch(List<Pair<Instance, List<PrecedingSucceedingTitle>>> batch,
    Function<Pair<Instance, List<PrecedingSucceedingTitle>>, Future<Void>> task) {
    ArrayList<Future<Void>> futures = new ArrayList<>();
    for (Pair<Instance, List<PrecedingSucceedingTitle>> instanceListPair : batch) {
      futures.add(task.apply(instanceListPair));
    }
    return Future.join(futures);
  }

  private Future<Void> handleInstanceUpsertFailure(AtomicInteger errorsCounter,
                                                   BulkProcessingErrorFileWriter errorsWriter,
                                                   Instance instance, Throwable e) {
    log.warn("handleInstanceUpsertFailure:: Failed to process single instance upsert operation, instanceId: '{}'",
      instance.getId(), e);
    errorsCounter.incrementAndGet();
    return errorsWriter.write(instance, Instance::getId, e);
  }

  private Future<Void> uploadErrorsFiles(BulkProcessingContext bulkContext) {
    return Future.join(
      vertx.executeBlocking(() ->
        folioS3Client.upload(bulkContext.getErrorEntitiesFileLocalPath(), bulkContext.getErrorEntitiesFilePath())),
      vertx.executeBlocking(() ->
        folioS3Client.upload(bulkContext.getErrorsFileLocalPath(), bulkContext.getErrorsFilePath()))
      )
      .compose(v -> Future.join(
        vertx.fileSystem().delete(bulkContext.getErrorEntitiesFileLocalPath()),
        vertx.fileSystem().delete(bulkContext.getErrorsFileLocalPath())
      ))
      .onFailure(
        e -> log.warn("uploadErrorsFiles:: Failed to upload bulk processing errors files to S3-like storage", e))
      .mapEmpty();
  }

}
