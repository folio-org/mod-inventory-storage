package org.folio.services.s3storage;

import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;

public class FolioS3ClientFactory {

  public static final String S3_URL_CONFIG = "S3_URL";
  public static final String S3_REGION_CONFIG = "S3_REGION";
  public static final String S3_BUCKET_CONFIG = "S3_BUCKET";
  public static final String S3_ACCESS_KEY_ID_CONFIG = "S3_ACCESS_KEY_ID";
  public static final String S3_SECRET_ACCESS_KEY_CONFIG = "S3_SECRET_ACCESS_KEY";
  public static final String S3_IS_AWS_CONFIG = "S3_IS_AWS";
  private static final String S3_URL_DEFAULT = "https://s3.amazonaws.com";
  private static final String S3_IS_AWS_DEFAULT = "false";

  public FolioS3Client getFolioS3Client() {
    return S3ClientFactory.getS3Client(getS3ClientProperties());
  }

  private S3ClientProperties getS3ClientProperties() {
    return S3ClientProperties
      .builder()
      .endpoint(getValue(S3_URL_CONFIG, S3_URL_DEFAULT))
      .region(getValue(S3_REGION_CONFIG))
      .bucket(getValue(S3_BUCKET_CONFIG))
      .accessKey(getValueOrEmpty(S3_ACCESS_KEY_ID_CONFIG))
      .secretKey(getValueOrEmpty(S3_SECRET_ACCESS_KEY_CONFIG))
      .awsSdk(Boolean.parseBoolean(getValue(S3_IS_AWS_CONFIG, S3_IS_AWS_DEFAULT)))
      .build();
  }

  private String getValue(String key) {
    return getValue(key, null);
  }

  private String getValue(String key, String defaultValue) {
    return System.getProperty(key, System.getenv().getOrDefault(key, defaultValue));
  }

  private String getValueOrEmpty(String key) {
    return getValue(key, "");
  }
}
