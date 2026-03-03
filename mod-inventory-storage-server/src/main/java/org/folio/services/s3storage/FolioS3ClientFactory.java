package org.folio.services.s3storage;

import static org.folio.utils.Environment.getBoolValue;
import static org.folio.utils.Environment.getValueOrEmpty;
import static org.folio.utils.Environment.getValueOrFail;

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
