package org.folio.rest.support;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.Json;
import java.nio.file.Path;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.InstanceBulkRequest;

public class BulkProcessingErrorFileWriter {

  private static final Logger log = LogManager.getLogger(BulkProcessingErrorFileWriter.class);
  private static final String ROOT_FOLDER = "temp/";
  private static final String FAILED_ENTITIES_FILE_SUFFIX = "_failedEntities";
  private static final String ERRORS_FILE_SUFFIX = "_errors";

  private final Vertx vertx;
  private String errorEntitiesFilePath;
  private String errorsFilePath;
  private AsyncFile errorEntitiesAsyncFile;
  private AsyncFile errorsAsyncFile;


  public BulkProcessingErrorFileWriter(Vertx vertx) {
    this.vertx = vertx;
  }

  public Future<Void> initialize(InstanceBulkRequest bulkRequest) {
    String recordsFilePath = StringUtils.removeStart(bulkRequest.getRecordsFileName(), '/');
    this.errorEntitiesFilePath = ROOT_FOLDER + recordsFilePath + FAILED_ENTITIES_FILE_SUFFIX;
    this.errorsFilePath = ROOT_FOLDER + recordsFilePath + ERRORS_FILE_SUFFIX;

    OpenOptions openOptions = new OpenOptions()
      .setWrite(true)
      .setAppend(true);

    Path parentDirectoryPath = Path.of(recordsFilePath).getParent();
    Future<Void> future = parentDirectoryPath != null
      ? vertx.fileSystem().mkdirs(parentDirectoryPath.toString())
      : Future.succeededFuture();

    return future
      .compose(v -> Future.all(
        vertx.fileSystem().open(errorEntitiesFilePath, openOptions),
        vertx.fileSystem().open(errorsFilePath, openOptions)
      ))
      .onSuccess(compositeFuture -> {
        this.errorEntitiesAsyncFile = compositeFuture.resultAt(0);
        this.errorsAsyncFile = compositeFuture.resultAt(1);
      })
      .onFailure(e -> log.warn("initialize:: Failed to initialize bulk processing errors files: '{}' and '{}'",
        errorsFilePath, errorsFilePath, e))
      .mapEmpty();
  }

  public <T> Future<Void> write(T entity, Function<T, String> entityIdExtractor, Throwable throwable) {
    Future<Void> entitiesWriteFuture = errorEntitiesAsyncFile.write(
      Buffer.buffer(Json.encode(entity) + System.lineSeparator()));
    Future<Void> errorsWriteFuture = errorsAsyncFile.write(
      Buffer.buffer(entityIdExtractor.apply(entity) + ", " + throwable.getMessage() + System.lineSeparator()));

    return Future.join(entitiesWriteFuture, errorsWriteFuture)
      .onFailure(e -> log.warn("write:: Failed to write bulk processing errors to the files: '{}' and '{}'",
        errorEntitiesFilePath, errorsFilePath, e))
      .mapEmpty();
  }

  public Future<Void> close() {
    return errorEntitiesAsyncFile.flush()
      .onFailure(e -> log.warn("close:: Failed to flush data to the file '{}'", errorEntitiesFilePath))
      .eventually(() -> errorEntitiesAsyncFile.close())
      .transform(ar -> errorsAsyncFile.flush())
      .onFailure(e -> log.warn("close:: Failed to flush data to the file '{}'", errorsFilePath))
      .eventually(() -> errorsAsyncFile.close());
  }

}
