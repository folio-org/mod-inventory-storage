package org.folio.services.instance;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceBulkRequest;
import org.folio.rest.jaxrs.model.InstanceBulkResponse;
import org.folio.rest.support.BulkProcessingErrorFileWriter;
import org.folio.s3.client.FolioS3Client;
import org.folio.services.BulkProcessingContext;
import org.folio.services.s3storage.FolioS3ClientFactory;

public class InstanceS3Service {

  private static final Logger log = LogManager.getLogger(InstanceS3Service.class);
  private final FolioS3ClientFactory folioS3ClientFactory;
  private final Vertx vertx;
  private final FolioS3Client folioS3Client;
  private final InstanceService instanceService;

  public InstanceS3Service(FolioS3ClientFactory folioS3ClientFactory, Vertx vertx, Map<String, String> okapiHeaders) {
    this.vertx = vertx;
    this.folioS3ClientFactory = folioS3ClientFactory;
    this.folioS3Client = folioS3ClientFactory.getFolioS3Client();
    this.instanceService = new InstanceService(vertx.getOrCreateContext(), okapiHeaders);
  }

  public Future<InstanceBulkResponse> processInstances(InstanceBulkRequest bulkRequest) {
    log.debug("processInstances:: Processing bulk instances request, filename: '{}'", bulkRequest.getRecordsFileName());
    return loadInstances(bulkRequest)
      .compose(instances -> upsert(instances, bulkRequest))
      .onFailure(e -> log.warn("Failed to process instances bulk request, filename: '{}'",
        bulkRequest.getRecordsFileName(), e));
  }

  private Future<List<Instance>> loadInstances(InstanceBulkRequest bulkRequest) {
    return vertx.executeBlocking(() -> {
      FolioS3Client s3Client = folioS3ClientFactory.getFolioS3Client();
      InputStream inputStream = s3Client.read(bulkRequest.getRecordsFileName());

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        return reader.lines()
          .map(instanceJson -> Json.decodeValue(instanceJson, Instance.class))
          .toList();
      }
    });
  }

  private Future<InstanceBulkResponse> upsert(List<Instance> instances, InstanceBulkRequest bulkRequest) {
    BulkProcessingContext bulkProcessingContext = new BulkProcessingContext(bulkRequest.getRecordsFileName());

    return instanceService.createInstances(instances, true, true)
      .map(rows -> new InstanceBulkResponse().withErrorsNumber(0))
      .recover(e -> processSequentially(instances, bulkProcessingContext));
  }

  private Future<InstanceBulkResponse> processSequentially(List<Instance> instances,
                                                           BulkProcessingContext bulkContext) {
    AtomicInteger errorsCounter = new AtomicInteger();
    BulkProcessingErrorFileWriter errorsWriter = new BulkProcessingErrorFileWriter(vertx);

    return errorsWriter.initialize(bulkContext)
      .map(v -> instances.stream()
        .map(instance -> instanceService.createInstances(List.of(instance), true, true)
          .onFailure(e -> handleInstanceUpsertFailure(errorsCounter, errorsWriter, instance, e)))
        .toList())
      .compose(futures -> Future.join(futures))
      .eventually(errorsWriter::close)
      .eventually(() -> uploadErrorsFiles(bulkContext))
      .transform(ar -> Future.succeededFuture(new InstanceBulkResponse()
        .withErrorsNumber(errorsCounter.get())
        .withErrorRecordsFileName(bulkContext.getErrorEntitiesFilePath())
        .withErrorsFileName(bulkContext.getErrorsFilePath())
      ));
  }

  private void handleInstanceUpsertFailure(AtomicInteger errorsCounter,
                                                  BulkProcessingErrorFileWriter errorsWriter,
                                                  Instance instance, Throwable e) {
    log.warn("processSequentially: Failed to process single instance upsert operation, instanceId: '{}'",
      instance.getId(), e);
    errorsCounter.incrementAndGet();
    errorsWriter.write(instance, Instance::getId, e);
  }

  private Future<Void> uploadErrorsFiles(BulkProcessingContext bulkContext) {
    return Future.join(
      vertx.executeBlocking(
        () -> folioS3Client.upload(bulkContext.getErrorEntitiesFileLocalPath(), bulkContext.getErrorEntitiesFilePath())),
      vertx.executeBlocking(
        () -> folioS3Client.upload(bulkContext.getErrorsFileLocalPath(), bulkContext.getErrorsFilePath()))
    )
    .compose(v -> Future.join(
      vertx.fileSystem().delete(bulkContext.getErrorEntitiesFileLocalPath()),
      vertx.fileSystem().delete(bulkContext.getErrorsFileLocalPath())
    ))
    .onFailure(e -> log.warn("uploadErrorsFiles:: Failed to upload bulk processing errors files to S3 like storage"))
    .mapEmpty();
  }

}
