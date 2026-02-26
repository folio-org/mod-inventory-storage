package org.folio.services.reindex;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.s3.client.FolioS3Client;

/**
 * Streams DB rows as NDJSON to S3 using multipart upload.
 *
 * <p>Rows are fetched via a {@link RowStream} (small DB cursor batches) and
 * written to a temp file. Once the file reaches {@link #MINIMAL_PART_SIZE} (5 MB)
 * a part is uploaded, the file is rotated, and streaming resumes. The final
 * (possibly smaller) part is uploaded in the {@code endHandler}, then the
 * multipart upload is completed. On any error the upload is aborted.
 */
public class ReindexS3ExportService {

  static final long MINIMAL_PART_SIZE = 5_242_880L; // 5 MB — S3 minimum part size
  private static final Logger log = LogManager.getLogger(ReindexS3ExportService.class);
  private final Context vertxContext;
  private final FolioS3Client s3Client;
  private final long minimalPartSize;

  public ReindexS3ExportService(Context vertxContext, FolioS3Client s3Client) {
    this(vertxContext, s3Client, MINIMAL_PART_SIZE);
  }

  /**
   * Package-private constructor for testing with a custom part-size threshold.
   */
  ReindexS3ExportService(Context vertxContext, FolioS3Client s3Client, long minimalPartSize) {
    this.vertxContext = vertxContext;
    this.s3Client = s3Client;
    this.minimalPartSize = minimalPartSize;
  }

  /**
   * Streams all rows from {@code rowStream} to the S3 object at {@code s3Key}
   * as newline-delimited JSON. Each row must have a {@link JsonObject} at column 0.
   *
   * @param rowStream stream of DB rows (column 0 is a JSON object)
   * @param s3Key     destination key inside the configured S3 bucket
   * @return a Future that completes when the upload is finished, or fails on error
   */
  public Future<Void> exportToS3(RowStream<Row> rowStream, String s3Key) {
    return vertxContext.executeBlocking(() -> s3Client.initiateMultipartUpload(s3Key))
      .compose(uploadId -> doExport(rowStream, s3Key, uploadId));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private Future<Void> doExport(RowStream<Row> rowStream, String s3Key, String uploadId) {
    Promise<Void> promise = Promise.promise();

    UploadContext ctx;
    try {
      ctx = new UploadContext(s3Key, uploadId);
    } catch (IOException e) {
      log.error("doExport:: failed to create temp file for key={}", s3Key, e);
      vertxContext.executeBlocking(() -> {
        try {
          s3Client.abortMultipartUpload(s3Key, uploadId);
        } catch (Exception ex) {
          log.warn("doExport:: failed to abort multipart upload for key={}", s3Key, ex);
        }
        return null;
      }).onComplete(v -> promise.fail(e));
      return promise.future();
    }

    rowStream
      .exceptionHandler(e -> abortAndFail(ctx, e, promise))
      .endHandler(v -> completeUpload(ctx, promise))
      .handler(row -> {
        rowStream.pause();
        try {
          ctx.writeRow(row.getJsonObject(0));
        } catch (IOException e) {
          abortAndFail(ctx, e, promise);
          return;
        }
        if (ctx.currentFileSize() >= minimalPartSize) {
          vertxContext.executeBlocking(ctx::uploadCurrentPart)
            .onSuccess(v -> rowStream.resume())
            .onFailure(e -> abortAndFail(ctx, e, promise));
        } else {
          rowStream.resume();
        }
      });

    return promise.future();
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private void completeUpload(UploadContext ctx, Promise<Void> promise) {
    if (promise.future().isComplete()) {
      return;
    }
    vertxContext.executeBlocking(
        () -> {
          ctx.flushAndClose();
          if (ctx.currentFileSize() > 0) {
            var etag = s3Client.uploadMultipartPart(ctx.s3Key, ctx.uploadId, ctx.partNumber++, ctx.tempFile.toString());
            ctx.partEtags.add(etag);
          }
          if (!ctx.partEtags.isEmpty()) {
            s3Client.completeMultipartUpload(ctx.s3Key, ctx.uploadId, ctx.partEtags);
          } else {
            // No rows exported: abort multipart and write an empty NDJSON object
            s3Client.abortMultipartUpload(ctx.s3Key, ctx.uploadId);
            s3Client.write(ctx.s3Key, InputStream.nullInputStream(), 0L);
          }
          ctx.cleanup();
          return null;
        })
      .onSuccess(v -> promise.complete())
      .onFailure(e -> {
        try {
          s3Client.abortMultipartUpload(ctx.s3Key, ctx.uploadId);
        } catch (Exception ex) {
          log.warn("completeUpload:: failed to abort multipart upload for key={}", ctx.s3Key, ex);
        }
        ctx.cleanup();
        if (!promise.future().isComplete()) {
          promise.fail(e);
        }
      });
  }

  private void abortAndFail(UploadContext ctx, Throwable cause, Promise<Void> promise) {
    if (promise.future().isComplete()) {
      return;
    }
    vertxContext.executeBlocking(() -> {
      try {
        s3Client.abortMultipartUpload(ctx.s3Key, ctx.uploadId);
      } catch (Exception e) {
        log.warn("abortAndFail:: failed to abort multipart upload for key={} uploadId={}",
          ctx.s3Key, ctx.uploadId, e);
      }
      ctx.cleanup();
      return null;
    }).onComplete(v -> {
      if (!promise.future().isComplete()) {
        promise.fail(cause);
      }
    });
  }

  /**
   * Holds mutable state for one in-progress multipart upload.
   * All field mutations happen either on the event loop (writeRow, currentFileSize)
   * or inside {@code executeBlocking} worker threads (uploadCurrentPart, cleanup),
   * never concurrently — back-pressure via {@code rowStream.pause/resume} ensures this.
   */
  private final class UploadContext {

    final String s3Key;
    final String uploadId;
    final List<String> partEtags = new ArrayList<>();

    Path tempFile;
    BufferedWriter writer;
    long fileSize;
    int partNumber = 1;

    UploadContext(String s3Key, String uploadId) throws IOException {
      this.s3Key = s3Key;
      this.uploadId = uploadId;
      rotateTempFile();
    }

    void writeRow(JsonObject json) throws IOException {
      var line = json.encode() + "\n";
      writer.write(line);
      fileSize += line.getBytes(StandardCharsets.UTF_8).length;
    }

    long currentFileSize() {
      return fileSize;
    }

    Void uploadCurrentPart() throws IOException {
      writer.flush();
      writer.close();
      var etag = s3Client.uploadMultipartPart(s3Key, uploadId, partNumber++, tempFile.toString());
      partEtags.add(etag);
      Files.deleteIfExists(tempFile);
      rotateTempFile();
      return null;
    }

    void flushAndClose() throws IOException {
      writer.flush();
      writer.close();
    }

    void cleanup() {
      try {
        if (writer != null) {
          writer.close();
        }
        if (tempFile != null) {
          Files.deleteIfExists(tempFile);
        }
      } catch (IOException e) {
        log.warn("cleanup:: failed to delete temp file {}", tempFile, e);
      }
    }

    private void rotateTempFile() throws IOException {
      tempFile = Files.createTempFile("reindex-export-", ".ndjson");
      writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8);
      fileSize = 0;
    }
  }
}
