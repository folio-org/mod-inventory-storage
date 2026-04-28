package org.folio.services.reindex;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.s3.exception.S3ClientException;

/**
 * Retry helper for transient S3 5xx errors on multipart-upload calls.
 *
 * <p>Background: as of {@code folio-s3-client} 3.0.x / 3.1.x-SNAPSHOT,
 * {@code AwsS3Client} extends {@code MinioS3Client} and only overrides the
 * single-shot {@code write(...)} methods. The multipart methods
 * ({@code initiateMultipartUpload}, {@code uploadMultipartPart},
 * {@code completeMultipartUpload}, {@code abortMultipartUpload}) are inherited
 * from {@code MinioS3Client} and use the {@code minio-java} client, which has
 * no retry on 5xx. Against real AWS S3 this surfaces every transient
 * {@code SlowDown} (HTTP 503) or {@code InternalError} (HTTP 500) as a hard
 * failure that bubbles up to the Kafka consumer and triggers a full message
 * redelivery — slowing the reindex pipeline several-fold.
 *
 * <p>This helper retries such transient errors with exponential backoff and
 * jitter, mimicking what the AWS SDK v2 would do natively if folio-s3-lib
 * delegated multipart calls to it.
 *
 * <p>Tunable via env vars:
 * <ul>
 *   <li>{@code S3_REINDEX_RETRY_MAX_ATTEMPTS} (default 5)</li>
 *   <li>{@code S3_REINDEX_RETRY_BASE_DELAY_MS} (default 200)</li>
 * </ul>
 */
//todo: remove when folio-s3-client uses aws client for multipart uploads in AwsS3Client
final class S3RetryableCalls {

  private static final Logger log = LogManager.getLogger(S3RetryableCalls.class);

  private S3RetryableCalls() {
  }

  /**
   * Executes {@code action}, retrying on retryable {@link S3ClientException}s
   * with exponential backoff (base * 2^(attempt-1)) and 50–100% jitter.
   *
   * @param op           short operation name for logging (e.g. {@code "initiateMultipartUpload"})
   * @param action       the S3 call to execute
   * @param maxAttempts  total attempts (including the first); must be &gt;= 1
   * @param baseDelayMs  base backoff in milliseconds
   * @param <T>          result type
   * @return result of the first successful attempt
   * @throws S3ClientException if all attempts fail or the error is non-retryable
   * @throws Exception         propagated from {@code action} (non-S3 exceptions are not retried)
   */
  @SuppressWarnings({
    "java:S2925", "BusyWait",  // Thread.sleep is intentional retry backoff, not busy-waiting
    "java:S2142"               // InterruptedException restores interrupt then propagates the S3 exception
  })
  static <T> T withRetry(String op, Callable<T> action, int maxAttempts, long baseDelayMs)
    throws Exception {
    int attempts = Math.max(1, maxAttempts);
    long base = Math.max(1, baseDelayMs);
    for (int attempt = 1; ; attempt++) {
      try {
        return action.call();
      } catch (S3ClientException e) {
        if (attempt >= attempts || !isRetryable(e)) {
          throw e;
        }
        long delay = computeBackoff(base, attempt);
        log.warn("withRetry:: {} attempt {}/{} failed (retryable: {}). Sleeping {} ms before retry.",
          op, attempt, attempts, rootMessage(e), delay);
        try {
          Thread.sleep(delay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  /**
   * Returns true if the exception (or any cause in its chain) indicates a
   * transient failure that is safe to retry.
   *
   * <p>Two categories are treated as retryable:
   * <ol>
   *   <li><b>Wire-level failures</b> — any {@link IOException} (covers
   *       {@code SocketException} "Broken pipe", {@code EOFException} "unexpected
   *       end of stream", {@code SSLException}, {@code SocketTimeoutException}, …)
   *       or {@link TimeoutException}. These never produced
   *       a server response, and S3 multipart parts are idempotent (S3 keys
   *       parts by {@code (uploadId, partNumber)} and only the ETag from the
   *       successful attempt is referenced at completion), so re-issuing the
   *       PUT is safe.</li>
   *   <li><b>Server-side 5xx</b> — message-substring match on the AWS / MinIO
   *       error envelopes for {@code SlowDown}, {@code InternalError},
   *       {@code ServiceUnavailable}, and any {@code 500}/{@code 503} status.</li>
   * </ol>
   */
  static boolean isRetryable(Throwable e) {
    Throwable c = e;
    while (c != null) {
      // (1) Transient by exception type — wire-level failure that produced no
      //     server response. Always safe to retry an idempotent S3 multipart call.
      if (c instanceof IOException
          || c instanceof TimeoutException) {
        return true;
      }
      // (2) Transient by message — server-side 5xx / throttling envelopes
      //     surfaced by MinIO as a generic exception with an embedded status.
      String m = c.getMessage();
      if (m != null && (m.contains("Response code: 503") || m.contains("Response code: 500")
          || m.contains("Status Code: 503") || m.contains("Status Code: 500")
          || m.contains("SlowDown") || m.contains("InternalError")
          || m.contains("ServiceUnavailable")
          || m.contains("unexpected end of stream")
          || m.contains("Broken pipe")
          || m.contains("Connection reset"))) {
        return true;
      }

      // Avoid infinite loops on self-referencing causes
      if (c.getCause() == c) {
        return false;
      }
      c = c.getCause();
    }
    return false;
  }

  private static long computeBackoff(long baseDelayMs, int attempt) {
    long exp = baseDelayMs * (1L << Math.min(attempt - 1, 10)); // cap shift to avoid overflow
    @SuppressWarnings("java:S2245") // pseudo-random is fine for retry-backoff jitter (not security-sensitive)
    double jitter = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5; // 50–100%
    return (long) (exp * jitter);
  }

  private static String rootMessage(Throwable e) {
    Throwable c = e;
    while (c.getCause() != null && c.getCause() != c) {
      c = c.getCause();
    }
    return c.getMessage();
  }
}

