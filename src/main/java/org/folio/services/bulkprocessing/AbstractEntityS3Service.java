package org.folio.services.bulkprocessing;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.commons.collections4.ListUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.folio.rest.jaxrs.model.BulkUpsertResponse;
import org.folio.rest.support.BulkProcessingErrorFileWriter;
import org.folio.s3.client.FolioS3Client;
import org.folio.services.BulkProcessingContext;
import org.folio.services.s3storage.FolioS3ClientFactory;

/**
 * The service that interacts with S3-compatible storage to perform upsert operations on entities retrieved
 * from external file. The file to be processed is specified through {@link BulkUpsertRequest}.
 * If an errors occurs during the processing, then uploads two files containing the failed entities
 * and their associated errors to S3-compatible storage.
 *
 * @param <T> - the type of the entities to perform a bulk upsert on
 * @param <R> - the type of the entity representation that will be witten to file with failed entities
 *              if processing errors occur. Usually, it can be the same as {@code <T>} type
 */
public abstract class AbstractEntityS3Service<T, R> {

  private static final Logger log = LogManager.getLogger(AbstractEntityS3Service.class);
  private static final String ENTITIES_PARALLEL_UPSERT_COUNT_PARAM = "bulk-processing.parallel.processBulkUpsert.count";
  private static final String DEFAULT_ENTITIES_PARALLEL_UPSERT_COUNT = "10";

  protected final Vertx vertx;
  protected final FolioS3Client s3Client;
  protected final int entitiesParallelUpsertLimit;

  protected AbstractEntityS3Service(FolioS3ClientFactory folioS3ClientFactory, Vertx vertx) {
    this.entitiesParallelUpsertLimit = Integer.parseInt(
      System.getProperty(ENTITIES_PARALLEL_UPSERT_COUNT_PARAM, DEFAULT_ENTITIES_PARALLEL_UPSERT_COUNT));
    this.vertx = vertx;
    this.s3Client = folioS3ClientFactory.getFolioS3Client();
  }

  /**
   * Processes a bulk request for entities by loading entities from the specified file in {@link BulkUpsertRequest}
   * located on S3-compatible storage, and upserts them into the database.
   * If an errors occurs during the processing, the method uploads two files containing the failed entities
   * and their associated errors to S3-compatible storage.
   *
   * @param bulkRequest - bulk entities request containing external file to be processed
   * @return {@link Future} of {@link BulkUpsertResponse} containing errors count, and files with failed entities
   *   and errors encountered during processing
   */
  public Future<BulkUpsertResponse> processBulkUpsert(BulkUpsertRequest bulkRequest) {
    log.debug("processBulkUpsert:: Processing bulk entities request, filename: '{}'", bulkRequest.getRecordsFileName());
    return loadEntities(bulkRequest)
      .compose(entities -> ensureEntitiesWithNonMarcControlledFieldsData(entities).map(entities))
      .compose(entities -> upsertEntities(entities, bulkRequest))
      .onFailure(e -> log.warn("processBulkUpsert:: Failed to process bulk entities request, filename: '{}'",
        bulkRequest.getRecordsFileName(), e));
  }

  private Future<List<T>> loadEntities(BulkUpsertRequest bulkRequest) {
    return vertx.executeBlocking(() -> {
      InputStream inputStream = s3Client.read(bulkRequest.getRecordsFileName());
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        return mapToEntities(reader.lines());
      }
    });
  }

  private Future<BulkUpsertResponse> upsertEntities(List<T> entities, BulkUpsertRequest bulkRequest) {
    BulkProcessingContext bulkProcessingContext = new BulkProcessingContext(bulkRequest);

    return upsert(entities, bulkProcessingContext.isPublishEvents())
      .map(v -> new BulkUpsertResponse().withErrorsNumber(0))
      .recover(e -> processSequentially(entities, bulkProcessingContext));
  }

  private Future<BulkUpsertResponse> processSequentially(List<T> entities, BulkProcessingContext bulkContext) {
    AtomicInteger errorsCounter = new AtomicInteger();
    BulkProcessingErrorFileWriter errorsWriter = new BulkProcessingErrorFileWriter(vertx, bulkContext);

    return errorsWriter.initialize()
      .compose(v -> processInBatches(entities, entity -> upsert(List.of(entity), bulkContext.isPublishEvents())
        .recover(e -> handleUpsertFailure(errorsCounter, errorsWriter, entity, e))))
      .eventually(errorsWriter::close)
      .eventually(() -> uploadErrorsFiles(bulkContext))
      .transform(ar -> Future.succeededFuture(new BulkUpsertResponse()
        .withErrorsNumber(errorsCounter.get())
        .withErrorRecordsFileName(bulkContext.getErrorEntitiesFilePath())
        .withErrorsFileName(bulkContext.getErrorsFilePath())
      ));
  }

  private Future<Void> processInBatches(List<T> entities, Function<T, Future<Void>> task) {
    Future<Void> future = Future.succeededFuture();
    List<List<T>> entitiesBatches = ListUtils.partition(entities, entitiesParallelUpsertLimit);

    for (List<T> batch : entitiesBatches) {
      future = future.eventually(() -> processBatch(batch, task));
    }
    return future;
  }

  private CompositeFuture processBatch(List<T> batch, Function<T, Future<Void>> task) {
    ArrayList<Future<Void>> futures = new ArrayList<>();
    for (T entitiesList : batch) {
      futures.add(task.apply(entitiesList));
    }
    return Future.join(futures);
  }

  private Future<Void> handleUpsertFailure(AtomicInteger errorsCounter, BulkProcessingErrorFileWriter errorsWriter,
                                           T entity, Throwable e) {
    R entityToWrite = provideEntityRepresentationForWritingErrors(entity);
    String entityId = extractEntityId(entityToWrite);
    log.warn("handleUpsertFailure:: Failed to process single entity upsert operation, entityId: '{}'",
      entityId, e);
    errorsCounter.incrementAndGet();
    return errorsWriter.write(entityToWrite, this::extractEntityId, e);
  }

  private Future<Void> uploadErrorsFiles(BulkProcessingContext bulkContext) {
    return Future.join(
      vertx.executeBlocking(() ->
        s3Client.upload(bulkContext.getErrorEntitiesFileLocalPath(), bulkContext.getErrorEntitiesFilePath())),
      vertx.executeBlocking(() ->
        s3Client.upload(bulkContext.getErrorsFileLocalPath(), bulkContext.getErrorsFilePath()))
    )
    .compose(v -> Future.join(
      vertx.fileSystem().delete(bulkContext.getErrorEntitiesFileLocalPath()),
      vertx.fileSystem().delete(bulkContext.getErrorsFileLocalPath())
    ))
    .onFailure(
      e -> log.warn("uploadErrorsFiles:: Failed to upload bulk processing errors files to S3-like storage", e))
    .mapEmpty();
  }

  /**
   * Maps a Stream of {@code lines} retrieved from external file to a list of entities of {@code <T>} type
   * suitable for processing in bulk upsert operation.
   *
   * @param linesStream - Stream of lines from an external file to be mapped to entities
   * @return a list of entities created from the provided lines
   */
  protected abstract List<T> mapToEntities(Stream<String> linesStream);

  /**
   * Ensures that the specified {@code entities} have their non-MARC controlled fields populated with data
   * from existing entities in the database.
   *
   * @param entities - entities to be populated with data
   * @return Future of Void
   */
  protected abstract Future<Void> ensureEntitiesWithNonMarcControlledFieldsData(List<T> entities);

  /**
   * Performs an upsert operation on specified list of {@code entities}.
   * The implementation of the upsert operation depends on the specifics of the {@code <T>} type of entity.
   *
   * @param entities      - a list of entities to be updated or created
   * @param publishEvents - a flag that indicates whether domain events should be published
   * @return Future of Void, succeeded if the upsert operation is successful, otherwise failed
   */
  protected abstract Future<Void> upsert(List<T> entities, boolean publishEvents);

  /**
   * Provides a representation of the given {@code entity} to be written to error file containing entities
   * that failed during processing. This method is intended to transform or extract the necessary information
   * from the {@code entity} for error logging purposes. Usually, the method can return the specified
   * {@code entity} itself, unless a different representation is required.
   *
   * @param entity - entity representation suitable for writing to file with failed entities
   * @return a representation of the {@code entity} suitable for writing to the file with failed entities
   */
  protected abstract R provideEntityRepresentationForWritingErrors(T entity);

  /**
   * Retrieves ID from the specified {@code entity} representation to be written to errors files.
   *
   * @param entity - entity to extract ID from
   * @return ID of the specified entity
   */
  protected abstract String extractEntityId(R entity);

}
