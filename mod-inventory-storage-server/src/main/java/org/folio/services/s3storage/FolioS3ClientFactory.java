package org.folio.services.s3storage;

import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;
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
  private static final String S3_IS_AWS_DEFAULT = "false";

  public static FolioS3Client getFolioS3Client(@NonNull S3ConfigType configType) {
    return S3ClientFactory.getS3Client(buildS3ClientProperties(configType));
  }

  public static String getBucketName(@NonNull S3ConfigType configType) {
    return getValueOrFail(getKey(S3_BUCKET_CONFIG, configType));
  }

  private static S3ClientProperties buildS3ClientProperties(@NonNull S3ConfigType configType) {
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
      .awsSdk(Boolean.parseBoolean(getValue(getKey(S3_IS_AWS_CONFIG, configType), S3_IS_AWS_DEFAULT)))
      .build();
  }

  private static String getKey(@NonNull String configName, @NonNull S3ConfigType configType) {
    return S3_PREFIX + configType + "_" + configName;
  }

  private static String getValue(@NonNull String key) {
    return getValue(key, null);
  }

  private static String getValue(@NonNull String key, @Nullable String defaultValue) {
    return System.getProperty(key, System.getenv().getOrDefault(key, defaultValue));
  }

  private static String getValueOrFail(@NonNull String key) {
    var value = getValue(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required S3 configuration property is missing: " + key);
    }
    return value;
  }

  private static String getValueOrEmpty(@NonNull String key) {
    return getValue(key, "");
  }

  public enum S3ConfigType {
    MARC_MIGRATION,
    REINDEX
  }
}
