package org.folio.services.reindex;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.services.s3storage.FolioS3ClientFactory.S3ConfigType.REINDEX;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.s3storage.FolioS3ClientFactory;

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
public class ReindexExportOrchestrator {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;

  public ReindexExportOrchestrator(Context vertxContext, Map<String, String> okapiHeaders,
                                   PostgresClient postgresClient) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.postgresClient = postgresClient;
  }

  /**
   * @param request        the range/job details from the HTTP request
   * @param streamProvider function that, given a DB {@link Conn}, returns the row stream to export
   */
  public Future<Void> export(ReindexRecordsRequest request,
                             Function<Conn, Future<RowStream<Row>>> streamProvider) {
    var s3Client = FolioS3ClientFactory.getFolioS3Client(REINDEX);
    var tenantId = okapiHeaders.get(TENANT);
    var traceId = StringUtils.isBlank(request.getTraceId()) ? UUID.randomUUID().toString() : request.getTraceId();
    var recordType = request.getRecordType().value();
    var s3Key = tenantId + "/" + recordType + "/" + traceId + "/" + request.getId() + ".ndjson";
    var rangeFrom = request.getRecordIdsRange().getFrom();
    var rangeTo = request.getRecordIdsRange().getTo();

    var exportService = new ReindexS3ExportService(vertxContext, s3Client);
    var eventPublisher = new ReindexFileReadyEventPublisher(vertxContext, okapiHeaders);

    return postgresClient.getConnection()
      .map(pgConnection -> new Conn(postgresClient, pgConnection))
      .compose(streamProvider)
      .compose(rowStream -> exportService.exportToS3(rowStream, s3Key))
      .compose(v -> {
        var event = ReindexFileReadyEvent.builder()
          .tenantId(tenantId)
          .recordType(recordType)
          .range(rangeFrom, rangeTo)
          .rangeId(request.getId())
          .jobId(traceId)
          .bucket(FolioS3ClientFactory.getBucketName(REINDEX))
          .objectKey(s3Key)
          .build();
        return eventPublisher.publish(event);
      });
  }
}
