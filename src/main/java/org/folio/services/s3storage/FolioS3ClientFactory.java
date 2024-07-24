package org.folio.services.s3storage;

import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;

public class FolioS3ClientFactory {

  public FolioS3Client getFolioS3Client() {
    return S3ClientFactory.getS3Client(getS3ClientProperties());
  }

  private S3ClientProperties getS3ClientProperties() {
    return S3ClientProperties
      .builder()
      .endpoint(getValue("AWS_URL"))
      .region(getValue("AWS_REGION"))
      .bucket(getValue("AWS_BUCKET"))
      .accessKey(getValue("AWS_ACCESS_KEY_ID"))
      .secretKey(getValue("AWS_SECRET_ACCESS_KEY"))
      .awsSdk(Boolean.parseBoolean(getValue("AWS_SDK")))
      .build();
  }

  private String getValue(String key) {
    return System.getProperty(key, System.getenv(key));
  }

}
