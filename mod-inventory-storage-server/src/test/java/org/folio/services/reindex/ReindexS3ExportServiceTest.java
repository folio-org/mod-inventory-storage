package org.folio.services.reindex;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.api.TestBase.get;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.s3.client.FolioS3Client;
import org.folio.s3.exception.S3ClientException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReindexS3ExportServiceTest {

  private static final String S3_KEY = "tenant/instance/trace/range.ndjson";
  private static final String UPLOAD_ID = "upload-id-1";
  private static final String ETAG = "etag-1";

  @Mock
  private Context vertxContext;
  @Mock
  private FolioS3Client s3Client;

  private ReindexS3ExportService exportService;

  @Before
  public void setUp() {
    // 1-byte threshold forces a part upload after every row.
    // 5 retry attempts with 1ms base delay so transient-failure tests are fast.
    exportService = new ReindexS3ExportService(vertxContext, s3Client, 1L, 5, 1L);
    when(vertxContext.<Object>executeBlocking(any())).thenAnswer(inv -> {
      try {
        return succeededFuture(inv.<java.util.concurrent.Callable<Object>>getArgument(0).call());
      } catch (Exception e) {
        return failedFuture(e);
      }
    });
  }

  private static S3ClientException slowDown503() {
    return new S3ClientException(
      "Error initiating multipart upload",
      new RuntimeException("Non-XML response from server. Response code: 503, Content-Type: application/xml"));
  }

  private static S3ClientException internalError500() {
    return new S3ClientException(
      "Error completing multipart upload",
      new RuntimeException("ErrorResponse(code = InternalError, message = We encountered an internal error.)"));
  }

  @Test
  public void exportToS3_noRows_abortsMultipartAndWritesEmptyFile() {
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenReturn(UPLOAD_ID);

    get(exportService.exportToS3(new TestRowStream(0), S3_KEY));

    verify(s3Client).abortMultipartUpload(S3_KEY, UPLOAD_ID);
    verify(s3Client).write(eq(S3_KEY), any(), eq(0L));
    verify(s3Client, never()).completeMultipartUpload(any(), any(), any());
  }

  @Test
  public void exportToS3_noRows_writesEmptyFileWithResettableStream() {
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenReturn(UPLOAD_ID);
    var streamCaptor = ArgumentCaptor.forClass(InputStream.class);

    get(exportService.exportToS3(new TestRowStream(0), S3_KEY));

    verify(s3Client).write(eq(S3_KEY), streamCaptor.capture(), eq(0L));
    InputStream captured = streamCaptor.getValue();
    assertThat(captured).isInstanceOf(ByteArrayInputStream.class);
    assertThat(captured.markSupported())
      .as("empty-write stream must support mark/reset for AWS SDK retry")
      .isTrue();
  }

  @Test
  public void exportToS3_singleRow_singlePartUploadedAndCompleted() {
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenReturn(UPLOAD_ID);
    when(s3Client.uploadMultipartPart(eq(S3_KEY), eq(UPLOAD_ID), anyInt(), any())).thenReturn(ETAG);

    get(exportService.exportToS3(new TestRowStream(1), S3_KEY));

    verify(s3Client).completeMultipartUpload(eq(S3_KEY), eq(UPLOAD_ID), any());
    verify(s3Client, never()).abortMultipartUpload(any(), any());
  }

  @Test
  public void exportToS3_multipleRows_multiplePartsUploadedAndCompleted() {
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenReturn(UPLOAD_ID);
    when(s3Client.uploadMultipartPart(eq(S3_KEY), eq(UPLOAD_ID), anyInt(), any())).thenReturn(ETAG);

    get(exportService.exportToS3(new TestRowStream(3), S3_KEY));

    verify(s3Client).completeMultipartUpload(eq(S3_KEY), eq(UPLOAD_ID), any());
    verify(s3Client, never()).abortMultipartUpload(any(), any());
  }

  @Test
  public void exportToS3_initiateMultipartFails_futureFailsWithoutAbort() {
    var cause = new RuntimeException("S3 unavailable");
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenThrow(cause);

    var exportFuture = exportService.exportToS3(new TestRowStream(1), S3_KEY);
    assertThrows(RuntimeException.class, () -> get(exportFuture));

    verify(s3Client, never()).abortMultipartUpload(any(), any());
  }

  // ---- Retry behaviour (regression: folio-s3-lib AwsS3Client doesn't override
  // multipart methods, so we wrap them with our own backoff retry) ----

  @Test
  public void exportToS3_initiateMultipart_retriesOnTransient503() {
    when(s3Client.initiateMultipartUpload(S3_KEY))
      .thenThrow(slowDown503())   // attempt 1: 503 SlowDown
      .thenThrow(slowDown503())   // attempt 2: 503 SlowDown
      .thenReturn(UPLOAD_ID);     // attempt 3: success
    when(s3Client.uploadMultipartPart(eq(S3_KEY), eq(UPLOAD_ID), anyInt(), any())).thenReturn(ETAG);

    get(exportService.exportToS3(new TestRowStream(1), S3_KEY));

    verify(s3Client, times(3)).initiateMultipartUpload(S3_KEY);
    verify(s3Client).completeMultipartUpload(eq(S3_KEY), eq(UPLOAD_ID), any());
  }

  @Test
  public void exportToS3_completeMultipart_retriesOnTransient500InternalError() {
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenReturn(UPLOAD_ID);
    when(s3Client.uploadMultipartPart(eq(S3_KEY), eq(UPLOAD_ID), anyInt(), any())).thenReturn(ETAG);
    org.mockito.Mockito.doThrow(internalError500())
      .doNothing()
      .when(s3Client).completeMultipartUpload(eq(S3_KEY), eq(UPLOAD_ID), any());

    get(exportService.exportToS3(new TestRowStream(1), S3_KEY));

    verify(s3Client, times(2)).completeMultipartUpload(eq(S3_KEY), eq(UPLOAD_ID), any());
    verify(s3Client, never()).abortMultipartUpload(any(), any());
  }

  @Test
  public void exportToS3_initiateMultipart_givesUpAfterMaxAttempts() {
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenThrow(slowDown503());

    var exportFuture = exportService.exportToS3(new TestRowStream(1), S3_KEY);
    assertThrows(RuntimeException.class, () -> get(exportFuture));

    // 5 attempts as configured in setUp
    verify(s3Client, times(5)).initiateMultipartUpload(S3_KEY);
  }

  @Test
  public void exportToS3_nonRetryableException_failsImmediately() {
    // S3ClientException with a 403 (auth) is not in the retryable list
    var authError = new S3ClientException("Access denied",
      new RuntimeException("Response code: 403, body: AccessDenied"));
    when(s3Client.initiateMultipartUpload(S3_KEY)).thenThrow(authError);

    var exportFuture = exportService.exportToS3(new TestRowStream(1), S3_KEY);
    assertThrows(RuntimeException.class, () -> get(exportFuture));

    verify(s3Client, times(1)).initiateMultipartUpload(S3_KEY);
  }
}
