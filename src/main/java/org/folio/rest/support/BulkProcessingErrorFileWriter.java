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

/**
 * The class is responsible for writing entities and their associated processing errors to separate files
 * during a bulk processing operation. The failed entities and processing errors are written to separate files,
 * which are specified through the {@link BulkProcessingContext}.
 * Before using the {@link #write(Object, Function, Throwable)} method, the writer must be initialized
 * by calling {@link #initialize()}.
 * When writing operations are completed, the {@link #close()} method should be called
 * to flush and close files properly.
 */
public class BulkProcessingErrorFileWriter {

  private static final Logger log = LogManager.getLogger(BulkProcessingErrorFileWriter.class);
  private static final String WRITER_IS_NOT_INITIALIZED_MSG =
    "BulkProcessingErrorFileWriter is not initialized, the BulkProcessingErrorFileWriter::initialize method "
      + "should be called prior BulkProcessingErrorFileWriter::write method";

  private final Vertx vertx;
  private final String failedEntitiesFilePath;
  private final String errorsFilePath;

  private AsyncFile errorEntitiesAsyncFile;
  private AsyncFile errorsAsyncFile;

  public BulkProcessingErrorFileWriter(Vertx vertx, BulkProcessingContext bulkContext) {
    this.vertx = vertx;
    this.failedEntitiesFilePath = bulkContext.getErrorEntitiesFileLocalPath();
    this.errorsFilePath = bulkContext.getErrorsFileLocalPath();
  }

  /**
   * Prepares the writer for writing errors to files by initializing the necessary resources.
   *
   * @return Future of Void
   */
  public Future<Void> initialize() {
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

  /**
   * Writes the {@code entity} and its associated processing error information to separate files.
   * <em><strong>Note</strong></em>: this method call should be preceded
   * by {@link BulkProcessingErrorFileWriter#initialize()} method call to initialize writer.
   * The method will throw {@link IllegalStateException} if the writer is not initialized.
   *
   * @param <T>               - the type of the entity to be written
   * @param entity            - the entity to be written to the failed entities file
   * @param entityIdExtractor - {@link Function} that extracts the ID from the specified {@code entity}
   * @param throwable         - the {@link Throwable} containing the error information to be written to the errors file
   * @return {@link Future} representing the completion of write operations to file with failed entities
   *   and to file containing their associated errors information
   * @throws IllegalStateException if the writer is not initialized
   */
  public <T> Future<Void> write(T entity, Function<T, String> entityIdExtractor, Throwable throwable) {
    if (!isInitialized()) {
      throw new IllegalStateException(WRITER_IS_NOT_INITIALIZED_MSG);
    }

    Future<Void> entitiesWriteFuture = errorEntitiesAsyncFile.write(
      Buffer.buffer(Json.encode(entity) + System.lineSeparator()));
    Future<Void> errorsWriteFuture = errorsAsyncFile.write(
      Buffer.buffer(entityIdExtractor.apply(entity) + ", " + throwable.getMessage() + System.lineSeparator()));

    return Future.join(entitiesWriteFuture, errorsWriteFuture)
      .onFailure(e -> log.warn("write:: Failed to write bulk processing errors to the files: '{}' and '{}'",
        failedEntitiesFilePath, errorsFilePath, e))
      .mapEmpty();
  }

  private boolean isInitialized() {
    return errorEntitiesAsyncFile != null && errorsAsyncFile != null;
  }

  /**
   * Flushes any remaining data to the files used for writing errors and then closes them,
   * releasing any system resources associated with the writer.
   *
   * @return Future of Void
   */
  public Future<Void> close() {
    return errorEntitiesAsyncFile.flush()
      .onFailure(e -> log.warn("close:: Failed to flush data to the file '{}'", failedEntitiesFilePath))
      .eventually(() -> errorEntitiesAsyncFile.close())
      .transform(ar -> errorsAsyncFile.flush())
      .onFailure(e -> log.warn("close:: Failed to flush data to the file '{}'", errorsFilePath))
      .eventually(() -> errorsAsyncFile.close());
  }

}
