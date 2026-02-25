package org.folio.services.reindex;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.api.TestBase.get;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import org.folio.rest.support.sql.TestRowStream;
import org.folio.s3.client.FolioS3Client;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    // 1-byte threshold forces a part upload after every row
    exportService = new ReindexS3ExportService(vertxContext, s3Client, 1L);
    when(vertxContext.<Object>executeBlocking(any())).thenAnswer(inv -> {
      try {
        return succeededFuture(inv.<java.util.concurrent.Callable<Object>>getArgument(0).call());
      } catch (Exception e) {
        return failedFuture(e);
      }
    });
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

    assertThrows(RuntimeException.class,
      () -> get(exportService.exportToS3(new TestRowStream(1), S3_KEY)));

    verify(s3Client, never()).abortMultipartUpload(any(), any());
  }
}
