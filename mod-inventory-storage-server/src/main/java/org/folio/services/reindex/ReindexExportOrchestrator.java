package org.folio.services.reindex;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.folio.persist.AbstractRepository;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.persist.Conn;
import org.folio.s3.client.FolioS3Client;
import org.folio.services.s3storage.FolioS3ClientFactory;
import org.folio.services.s3storage.FolioS3ClientFactory.S3ConfigType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the reindex export flow shared by all record types:
 * <ol>
 *   <li>Stream rows from DB via the caller-supplied {@code streamProvider}</li>
 *   <li>Write them as NDJSON to S3 using multipart upload</li>
 *   <li>Publish a {@link ReindexFileReadyEvent} to Kafka on success</li>
 * </ol>
 * Callers supply only the record-type-specific stream via a {@link Function} over {@link Conn},
 * keeping all infrastructure concerns in one place.
 */
@Service
public class ReindexExportOrchestrator {

  private final Context vertxContext;
  private final Supplier<FolioS3Client> s3ClientSupplier;
  private final Supplier<String> bucketNameSupplier;
  private final ReindexFileReadyEventPublisher eventPublisher;

  @Autowired
  public ReindexExportOrchestrator(Context vertxContext, ReindexFileReadyEventPublisher eventPublisher) {
    // S3 is optional at startup, so the client and bucket are resolved when an export runs.
    this(vertxContext, () -> FolioS3ClientFactory.getFolioS3Client(S3ConfigType.REINDEX),
      () -> FolioS3ClientFactory.getBucketName(S3ConfigType.REINDEX), eventPublisher);
  }

  /**
   * Package-private constructor for testing with injected dependencies.
   */
  ReindexExportOrchestrator(Context vertxContext, Supplier<FolioS3Client> s3ClientSupplier,
                            Supplier<String> bucketNameSupplier, ReindexFileReadyEventPublisher eventPublisher) {
    this.vertxContext = vertxContext;
    this.s3ClientSupplier = s3ClientSupplier;
    this.bucketNameSupplier = bucketNameSupplier;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Orchestrates the export of a range of records to S3 and the publication of the corresponding "file ready" event.
   * The exported file will be named according to the pattern: {tenantId}/{recordType}/{traceId}/{rangeId}.ndjson.
   *
   * @param request        the range/job details from the HTTP request
   * @param headers   the okapi headers of the request
   * @param repository     the repository whose table is exported
   * @param streamProvider function that, given a DB {@link Conn}, returns the row stream to export
   */
  public Future<Void> export(ReindexRecordsRequest request, Map<String, String> headers,
                             AbstractRepository<?> repository,
                             Function<Conn, Future<RowStream<Row>>> streamProvider) {
    var s3Client = s3ClientSupplier.get();
    var tenantId = headers.get(TENANT);
    var traceId = StringUtils.isBlank(request.getTraceId()) ? UUID.randomUUID().toString() : request.getTraceId();
    var recordType = request.getRecordType().value();
    var s3Key = tenantId + "/" + recordType + "/" + traceId + "/" + request.getId() + ".ndjson";
    var rangeFrom = request.getRecordIdsRange().getFrom();
    var rangeTo = request.getRecordIdsRange().getTo();
    var exportService = new ReindexS3ExportService(vertxContext, s3Client);

    return repository.inTransaction(tenantId, conn -> streamProvider.apply(conn)
        .compose(rowStream -> exportService.exportToS3(rowStream, s3Key)))
      .compose(v -> {
        var event = ReindexFileReadyEvent.builder()
          .tenantId(tenantId)
          .recordType(recordType)
          .range(rangeFrom, rangeTo)
          .rangeId(request.getId())
          .traceId(traceId)
          .bucket(bucketNameSupplier.get())
          .objectKey(s3Key)
          .build();
        return eventPublisher.publish(event, headers);
      });
  }
}
