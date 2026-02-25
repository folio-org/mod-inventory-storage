package org.folio.services.reindex;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.api.TestBase.get;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.rest.jaxrs.model.RecordIdsRange;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.s3.client.FolioS3Client;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReindexExportOrchestratorTest {

  private static final String TENANT_ID = "test-tenant";
  private static final String FROM_ID = "00000000-0000-0000-0000-000000000000";
  private static final String TO_ID = "ffffffff-ffff-ffff-ffff-ffffffffffff";
  private static final String RANGE_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String TRACE_ID = "660e8400-e29b-41d4-a716-446655440000";
  private static final String BUCKET = "test-bucket";
  private static final String UPLOAD_ID = "upload-id";
  private static final String ETAG = "etag-1";

  @Mock
  private Context vertxContext;
  @Mock
  private PostgresClient postgresClient;
  @Mock
  private Conn conn;
  @Mock
  private FolioS3Client s3Client;
  @Mock
  private ReindexFileReadyEventPublisher eventPublisher;

  private ReindexExportOrchestrator orchestrator;

  @Before
  public void setUp() {
    orchestrator = new ReindexExportOrchestrator(vertxContext,
      new CaseInsensitiveMap<>(Map.of(TENANT, TENANT_ID)),
      postgresClient, s3Client, BUCKET, eventPublisher);
    when(vertxContext.<Object>executeBlocking(any())).thenAnswer(inv -> {
      try {
        return succeededFuture(inv.<java.util.concurrent.Callable<Object>>getArgument(0).call());
      } catch (Exception e) {
        return failedFuture(e);
      }
    });
    when(postgresClient.withTrans(any())).thenAnswer(inv -> {
      var fn = inv.<Function<Conn, Future<?>>>getArgument(0);
      return fn.apply(conn);
    });
  }

  @Test
  public void export_noRows_abortsMultipartWritesEmptyAndPublishesEvent() {
    when(s3Client.initiateMultipartUpload(any())).thenReturn(UPLOAD_ID);
    when(eventPublisher.publish(any())).thenReturn(succeededFuture());

    get(orchestrator.export(buildRequest(TRACE_ID),
      c -> succeededFuture(new TestRowStream(0))));

    verify(s3Client).abortMultipartUpload(any(), eq(UPLOAD_ID));
    verify(s3Client).write(any(), any(), eq(0L));
    verify(eventPublisher).publish(any());
  }

  @Test
  public void export_rowsPresent_multipartCompletedAndEventPublished() {
    when(s3Client.initiateMultipartUpload(any())).thenReturn(UPLOAD_ID);
    when(s3Client.uploadMultipartPart(any(), eq(UPLOAD_ID), anyInt(), any())).thenReturn(ETAG);
    when(eventPublisher.publish(any())).thenReturn(succeededFuture());

    get(orchestrator.export(buildRequest(TRACE_ID),
      c -> succeededFuture(new TestRowStream(2))));

    verify(s3Client).completeMultipartUpload(any(), eq(UPLOAD_ID), any());
    verify(eventPublisher).publish(any());
  }

  @Test
  public void export_blankTraceId_eventStillPublished() {
    when(s3Client.initiateMultipartUpload(any())).thenReturn(UPLOAD_ID);
    when(eventPublisher.publish(any())).thenReturn(succeededFuture());

    get(orchestrator.export(buildRequest(""),
      c -> succeededFuture(new TestRowStream(0))));

    verify(eventPublisher).publish(any());
  }

  @Test
  public void export_streamProviderFails_futureFailedAndEventNotPublished() {
    assertThrows(RuntimeException.class, () -> get(orchestrator.export(buildRequest(TRACE_ID),
      c -> failedFuture("stream error"))));

    verify(eventPublisher, never()).publish(any());
  }

  private static ReindexRecordsRequest buildRequest(String traceId) {
    return new ReindexRecordsRequest()
      .withId(RANGE_ID)
      .withTraceId(traceId)
      .withRecordType(ReindexRecordsRequest.RecordType.INSTANCE)
      .withRecordIdsRange(new RecordIdsRange().withFrom(FROM_ID).withTo(TO_ID));
  }
}
