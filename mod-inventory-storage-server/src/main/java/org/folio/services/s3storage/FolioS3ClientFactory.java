package org.folio.services.s3storage;

import static org.folio.utils.Environment.getBoolValue;
import static org.folio.utils.Environment.getValueOrEmpty;
import static org.folio.utils.Environment.getValueOrFail;
import static org.folio.utils.Environment.parseOptionalInt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;
import org.folio.s3.exception.S3ClientException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class FolioS3ClientFactory {

  private static final String S3_PREFIX = "S3_";
  private static final String S3_URL_CONFIG = "URL";
  private static final String S3_REGION_CONFIG = "REGION";
  private static final String S3_BUCKET_CONFIG = "BUCKET";
  private static final String S3_ACCESS_KEY_ID_CONFIG = "ACCESS_KEY_ID";
  private static final String S3_SECRET_ACCESS_KEY_CONFIG = "SECRET_ACCESS_KEY";
  private static final String S3_IS_AWS_CONFIG = "IS_AWS";
  private static final String S3_IDLE_KEEP_ALIVE_SECONDS_CONFIG = "IDLE_KEEP_ALIVE_SECONDS";
  private static final String S3_MAX_IDLE_CONNECTIONS_CONFIG = "MAX_IDLE_CONNECTIONS";
  private static final String S3_MAX_REQUESTS_PER_HOST_CONFIG = "MAX_REQUESTS_PER_HOST";

  private static final Boolean S3_IS_AWS_DEFAULT = Boolean.FALSE;

  // Null S3ConfigType (generic S3_ prefix) is stored under this sentinel key.
  private static final String DEFAULT_CONFIG_KEY = "_DEFAULT_";
  private static final Map<String, FolioS3Client> CLIENT_CACHE = new ConcurrentHashMap<>();

  /**
   * Returns a cached {@link FolioS3Client} for the given config type, creating it on first call.
   *
   * <p>Caching ensures a single OkHttp connection pool is reused across all requests of the same
   * type, eliminating per-request TCP/TLS handshake overhead and enabling connection reuse.
   */
  public static FolioS3Client getFolioS3Client(@Nullable S3ConfigType configType) {
    String key = configType != null ? configType.name() : DEFAULT_CONFIG_KEY;
    return CLIENT_CACHE.computeIfAbsent(key, k -> createClient(configType));
  }

  public static String getBucketName(@NonNull S3ConfigType configType) {
    return getValueOrFail(getKey(S3_BUCKET_CONFIG, configType));
  }

  /**
   * Clears the client cache. Intended for use in tests only.
   */
  static void clearCache() {
    CLIENT_CACHE.clear();
  }

  private static FolioS3Client createClient(@Nullable S3ConfigType configType) {
    try {
      return S3ClientFactory.getS3Client(buildS3ClientProperties(configType));
    } catch (IllegalStateException e) {
      throw new S3ClientException(e.getMessage());
    }
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
      .idleKeepAliveSeconds(parseOptionalInt(getKey(S3_IDLE_KEEP_ALIVE_SECONDS_CONFIG, configType)))
      .maxIdleConnections(parseOptionalInt(getKey(S3_MAX_IDLE_CONNECTIONS_CONFIG, configType)))
      .maxRequestsPerHost(parseOptionalInt(getKey(S3_MAX_REQUESTS_PER_HOST_CONFIG, configType)))
      .build();
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
