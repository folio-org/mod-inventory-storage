package org.folio.rest.support;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.Json;
import java.nio.file.Path;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.services.BulkProcessingContext;

public class BulkProcessingErrorFileWriter {

  private static final Logger log = LogManager.getLogger(BulkProcessingErrorFileWriter.class);

  private final Vertx vertx;
  private String failedEntitiesFilePath;
  private String errorsFilePath;
  private AsyncFile errorEntitiesAsyncFile;
  private AsyncFile errorsAsyncFile;


  public BulkProcessingErrorFileWriter(Vertx vertx) {
    this.vertx = vertx;
  }

  public Future<Void> initialize(BulkProcessingContext bulkContext) {
    this.failedEntitiesFilePath = bulkContext.getErrorEntitiesFileLocalPath();
    this.errorsFilePath = bulkContext.getErrorsFileLocalPath();

    OpenOptions openOptions = new OpenOptions()
      .setWrite(true)
      .setAppend(true);

    return ensureParentDirectory(failedEntitiesFilePath)
      .compose(v -> ensureParentDirectory(errorsFilePath))
      .compose(v -> Future.all(
        vertx.fileSystem().open(failedEntitiesFilePath, openOptions),
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

  private Future<Void> ensureParentDirectory(String filePath) {
    Path parentPath = Path.of(filePath).getParent();
    return parentPath != null
      ? vertx.fileSystem().mkdirs(parentPath.toString())
      : Future.succeededFuture();
  }

  public <T> Future<Void> write(T entity, Function<T, String> entityIdExtractor, Throwable throwable) {
    Future<Void> entitiesWriteFuture = errorEntitiesAsyncFile.write(
      Buffer.buffer(Json.encode(entity) + System.lineSeparator()));
    Future<Void> errorsWriteFuture = errorsAsyncFile.write(
      Buffer.buffer(entityIdExtractor.apply(entity) + ", " + throwable.getMessage() + System.lineSeparator()));

    return Future.join(entitiesWriteFuture, errorsWriteFuture)
      .onFailure(e -> log.warn("write:: Failed to write bulk processing errors to the files: '{}' and '{}'",
        failedEntitiesFilePath, errorsFilePath, e))
      .mapEmpty();
  }

  public Future<Void> close() {
    return errorEntitiesAsyncFile.flush()
      .onFailure(e -> log.warn("close:: Failed to flush data to the file '{}'", failedEntitiesFilePath))
      .eventually(() -> errorEntitiesAsyncFile.close())
      .transform(ar -> errorsAsyncFile.flush())
      .onFailure(e -> log.warn("close:: Failed to flush data to the file '{}'", errorsFilePath))
      .eventually(() -> errorsAsyncFile.close());
  }

}
