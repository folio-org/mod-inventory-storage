package org.folio.services.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
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
    "ErrorResponse(code = ServiceUnavailable)"
  })
  void isRetryable_returnsTrueForTransient5xxMessages(String causeMessage) {
    var ex = new S3ClientException("err", new RuntimeException(causeMessage));
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
    String result = S3RetryableCalls.withRetry("op", () -> {
      calls.incrementAndGet();
      return "ok";
    }, 5, 1L);
    assertThat(result).isEqualTo("ok");
    assertThat(calls).hasValue(1);
  }

  @Test
  void withRetry_succeedsAfterTransientFailures() throws Exception {
    var calls = new AtomicInteger();
    String result = S3RetryableCalls.withRetry("op", () -> {
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
}
