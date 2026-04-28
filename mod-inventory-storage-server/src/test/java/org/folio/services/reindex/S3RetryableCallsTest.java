package org.folio.services.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;
import org.folio.s3.exception.S3ClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class S3RetryableCallsTest {

  @ParameterizedTest(name = "[{index}] retryable: \"{0}\"")
  @ValueSource(strings = {
    // minio-java surface
    "Non-XML response from server. Response code: 503, Content-Type: application/xml",
    "Non-XML response from server. Response code: 500",
    // AWS SDK surface
    "(Service: S3, Status Code: 503, Request ID: ABC123)",
    "(Service: S3, Status Code: 500, Request ID: XYZ)",
    // AWS error codes
    "ErrorResponse(code = InternalError, message = ...)",
    "ErrorResponse(code = SlowDown)",
    "ErrorResponse(code = ServiceUnavailable)",
    // Wire-level failures surfaced as plain string causes
    "unexpected end of stream on https://...",
    "Broken pipe",
    "Connection reset"
  })
  void isRetryable_returnsTrueForTransient5xxMessages(String causeMessage) {
    var ex = new S3ClientException("err", new RuntimeException(causeMessage));
    assertThat(S3RetryableCalls.isRetryable(ex)).isTrue();
  }

  @ParameterizedTest(name = "[{index}] retryable IOException subtype: {0}")
  @ValueSource(classes = {
    IOException.class,
    EOFException.class,
    SocketException.class,
    SocketTimeoutException.class,
    SSLException.class,
    TimeoutException.class
  })
  void isRetryable_returnsTrueForWireLevelExceptionTypes(Class<? extends Exception> exType) throws Exception {
    // Wrap the wire-level exception inside an S3ClientException, like MinioS3Client does.
    var cause = exType.getDeclaredConstructor(String.class).newInstance("boom");
    var ex = new S3ClientException("Cannot upload part # 2 for upload ID: …", cause);
    assertThat(S3RetryableCalls.isRetryable(ex)).isTrue();
  }

  @Test
  void isRetryable_returnsTrueForUploadPartChain() {
    var eof = new EOFException("\\n not found: limit=0 content=…");
    var io = new IOException("unexpected end of stream on https:///...", eof);
    var execEx = new ExecutionException(io);
    var ex = new S3ClientException("Cannot upload part # 2 for upload ID: …", execEx);
    assertThat(S3RetryableCalls.isRetryable(ex)).isTrue();
  }

  @ParameterizedTest(name = "[{index}] non-retryable: \"{0}\"")
  @ValueSource(strings = {
    "Response code: 403, AccessDenied",
    "Response code: 404, NoSuchKey",
    "Response code: 400, InvalidRequest",
    "some unrelated error"
  })
  void isRetryable_returnsFalseForNonTransientErrors(String causeMessage) {
    var ex = new S3ClientException("x", new RuntimeException(causeMessage));
    assertThat(S3RetryableCalls.isRetryable(ex)).isFalse();
  }

  @Test
  void isRetryable_walksCauseChain() {
    var nested = new RuntimeException("wrapper",
      new RuntimeException("Response code: 503"));
    var ex = new S3ClientException("err", nested);
    assertThat(S3RetryableCalls.isRetryable(ex)).isTrue();
  }

  @Test
  void isRetryable_returnsFalseForNullMessage() {
    var ex = new S3ClientException("x", new RuntimeException((String) null));
    assertThat(S3RetryableCalls.isRetryable(ex)).isFalse();
  }

  @Test
  void withRetry_returnsFirstAttemptResult_whenNoFailure() throws Exception {
    var calls = new AtomicInteger();
    var result = S3RetryableCalls.withRetry("op", () -> {
      calls.incrementAndGet();
      return "ok";
    }, 5, 1L);
    assertThat(result).isEqualTo("ok");
    assertThat(calls).hasValue(1);
  }

  @Test
  void withRetry_succeedsAfterTransientFailures() throws Exception {
    var calls = new AtomicInteger();
    var result = S3RetryableCalls.withRetry("op", () -> {
      int n = calls.incrementAndGet();
      if (n < 3) {
        throw new S3ClientException("transient",
          new RuntimeException("Response code: 503"));
      }
      return "ok";
    }, 5, 1L);
    assertThat(result).isEqualTo("ok");
    assertThat(calls).hasValue(3);
  }

  @Test
  void withRetry_givesUpAfterMaxAttempts_andRethrowsOriginal() {
    var calls = new AtomicInteger();
    assertThatThrownBy(() ->
      S3RetryableCalls.withRetry("op", () -> {
        calls.incrementAndGet();
        throw new S3ClientException("persistent",
          new RuntimeException("Response code: 503"));
      }, 3, 1L)
    ).isInstanceOf(S3ClientException.class)
     .hasMessage("persistent");
    assertThat(calls).hasValue(3);
  }

  @Test
  void withRetry_doesNotRetryNonRetryableS3Exception() {
    var calls = new AtomicInteger();
    assertThatThrownBy(() ->
      S3RetryableCalls.withRetry("op", () -> {
        calls.incrementAndGet();
        throw new S3ClientException("auth",
          new RuntimeException("Response code: 403"));
      }, 5, 1L)
    ).isInstanceOf(S3ClientException.class);
    assertThat(calls).hasValue(1);
  }

  @Test
  void withRetry_doesNotRetryArbitraryException() {
    var calls = new AtomicInteger();
    assertThatThrownBy(() ->
      S3RetryableCalls.withRetry("op", () -> {
        calls.incrementAndGet();
        throw new IllegalStateException("not s3");
      }, 5, 1L)
    ).isInstanceOf(IllegalStateException.class);
    assertThat(calls).hasValue(1);
  }

  @Test
  void withRetry_retriesUploadPartEofChain_thenSucceeds() throws Exception {
    var calls = new AtomicInteger();
    var result = S3RetryableCalls.withRetry("uploadMultipartPart#2", () -> {
      int n = calls.incrementAndGet();
      if (n < 2) {
        // Same shape as a real EOF on a stale-pooled S3 connection.
        var io = new IOException("unexpected end of stream on https://.../...");
        var execEx = new ExecutionException(io);
        throw new S3ClientException("Cannot upload part # 2 for upload ID: …", execEx);
      }
      return "etag";
    }, 5, 1L);
    assertThat(result).isEqualTo("etag");
    assertThat(calls).hasValue(2);
  }
}
