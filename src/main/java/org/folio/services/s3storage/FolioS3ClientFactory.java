package org.folio.services.s3storage;

import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;

public class FolioS3ClientFactory {

  public static final String AWS_URL_CONFIG = "AWS_URL";
  public static final String AWS_REGION_CONFIG = "AWS_REGION";
  public static final String AWS_BUCKET_CONFIG = "AWS_BUCKET";
  public static final String AWS_ACCESS_KEY_ID_CONFIG = "AWS_ACCESS_KEY_ID";
  public static final String AWS_SECRET_ACCESS_KEY_CONFIG = "AWS_SECRET_ACCESS_KEY";
  public static final String AWS_SDK_CONFIG = "AWS_SDK";

  public FolioS3Client getFolioS3Client() {
    return S3ClientFactory.getS3Client(getS3ClientProperties());
  }

  private S3ClientProperties getS3ClientProperties() {
    return S3ClientProperties
      .builder()
      .endpoint(getValue(AWS_URL_CONFIG))
      .region(getValue(AWS_REGION_CONFIG))
      .bucket(getValue(AWS_BUCKET_CONFIG))
      .accessKey(getValue(AWS_ACCESS_KEY_ID_CONFIG))
      .secretKey(getValue(AWS_SECRET_ACCESS_KEY_CONFIG))
      .awsSdk(Boolean.parseBoolean(getValue(AWS_SDK_CONFIG)))
      .build();
  }

  private String getValue(String key) {
    return System.getProperty(key, System.getenv(key));
  }

}
