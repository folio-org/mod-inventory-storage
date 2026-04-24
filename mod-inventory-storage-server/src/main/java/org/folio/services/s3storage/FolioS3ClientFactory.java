package org.folio.services.s3storage;

import static org.folio.utils.Environment.getBoolValue;
import static org.folio.utils.Environment.getValueOrEmpty;
import static org.folio.utils.Environment.getValueOrFail;

import org.apache.commons.lang3.StringUtils;
import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;
import org.folio.s3.exception.S3ClientException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class FolioS3ClientFactory {

  /**
   * Idle keep-alive (seconds) for the OkHttp connection pool used by the
   * reindex S3 client. Optional override; when unset, folio-s3-client uses
   * OkHttp's default ({@code 5 minutes}).
   *
   * <p>Lowering this was tried (15 s) to mitigate {@code unexpected end of
   * stream} / {@code Broken pipe} on stale pooled sockets, but in practice
   * it more than doubled the failure rate by churning through new TLS
   * handshakes — so we leave it unset by default and rely on the lazy
   * multipart-init flow + {@code S3RetryableCalls} retries instead. The env
   * variable remains here only for further experimentation.
   *
   * <p>Only applied to {@link S3ConfigType#REINDEX}; for other config types
   * the property is left {@code null}.
   */
  static final String REINDEX_OKHTTP_IDLE_KEEPALIVE_SECONDS_ENV =
    "S3_REINDEX_OKHTTP_IDLE_KEEPALIVE_SECONDS";

  private static final String S3_PREFIX = "S3_";
  private static final String S3_URL_CONFIG = "URL";
  private static final String S3_REGION_CONFIG = "REGION";
  private static final String S3_BUCKET_CONFIG = "BUCKET";
  private static final String S3_ACCESS_KEY_ID_CONFIG = "ACCESS_KEY_ID";
  private static final String S3_SECRET_ACCESS_KEY_CONFIG = "SECRET_ACCESS_KEY";
  private static final String S3_IS_AWS_CONFIG = "IS_AWS";
  private static final Boolean S3_IS_AWS_DEFAULT = Boolean.FALSE;

  public static FolioS3Client getFolioS3Client(@Nullable S3ConfigType configType) {
    try {
      return S3ClientFactory.getS3Client(buildS3ClientProperties(configType));
    } catch (IllegalStateException e) {
      throw new S3ClientException(e.getMessage());
    }
  }

  public static String getBucketName(@NonNull S3ConfigType configType) {
    return getValueOrFail(getKey(S3_BUCKET_CONFIG, configType));
  }

  private static S3ClientProperties buildS3ClientProperties(@Nullable S3ConfigType configType) {
    var url = getValueOrFail(getKey(S3_URL_CONFIG, configType));
    var region = getValueOrFail(getKey(S3_REGION_CONFIG, configType));
    var bucket = getValueOrFail(getKey(S3_BUCKET_CONFIG, configType));
    return S3ClientProperties
      .builder()
      .endpoint(url)
      .region(region)
      .bucket(bucket)
      .accessKey(getValueOrEmpty(getKey(S3_ACCESS_KEY_ID_CONFIG, configType)))
      .secretKey(getValueOrEmpty(getKey(S3_SECRET_ACCESS_KEY_CONFIG, configType)))
      .awsSdk(getBoolValue(getKey(S3_IS_AWS_CONFIG, configType), S3_IS_AWS_DEFAULT))
      .idleKeepAliveSeconds(resolveIdleKeepAliveSeconds(configType))
      .build();
  }

  private static @Nullable Integer resolveIdleKeepAliveSeconds(@Nullable S3ConfigType configType) {
    if (configType != S3ConfigType.REINDEX) {
      return null;
    }
    var raw = getValueOrEmpty(REINDEX_OKHTTP_IDLE_KEEPALIVE_SECONDS_ENV);
    return StringUtils.isBlank(raw) ? null : Integer.valueOf(raw);
  }

  private static String getKey(@NonNull String configName, @Nullable S3ConfigType configType) {
    if (configType == null) {
      return S3_PREFIX + configName;
    }
    return S3_PREFIX + configType + "_" + configName;
  }

  public enum S3ConfigType {
    MARC_MIGRATION,
    REINDEX
  }
}
