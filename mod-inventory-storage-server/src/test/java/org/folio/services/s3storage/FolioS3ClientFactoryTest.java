package org.folio.services.s3storage;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.folio.services.s3storage.FolioS3ClientFactory.S3ConfigType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FolioS3ClientFactoryTest {

  private static final String ENDPOINT = "http://localhost:9000";
  private static final String REGION = "us-east-1";
  private static final String BUCKET = "test-bucket";

  @BeforeEach
  void setUpS3Properties() {
    System.setProperty("S3_REINDEX_URL", ENDPOINT);
    System.setProperty("S3_REINDEX_REGION", REGION);
    System.setProperty("S3_REINDEX_BUCKET", BUCKET);
    System.setProperty("S3_MARC_MIGRATION_URL", ENDPOINT);
    System.setProperty("S3_MARC_MIGRATION_REGION", REGION);
    System.setProperty("S3_MARC_MIGRATION_BUCKET", BUCKET);
    // Clear the cache before each test so tests are independent.
    FolioS3ClientFactory.clearCache();
  }

  @AfterEach
  void clearS3Properties() {
    System.clearProperty("S3_REINDEX_URL");
    System.clearProperty("S3_REINDEX_REGION");
    System.clearProperty("S3_REINDEX_BUCKET");
    System.clearProperty("S3_MARC_MIGRATION_URL");
    System.clearProperty("S3_MARC_MIGRATION_REGION");
    System.clearProperty("S3_MARC_MIGRATION_BUCKET");
    FolioS3ClientFactory.clearCache();
  }

  @Test
  void sameInstanceReturnedForSameConfigType() {
    var first = FolioS3ClientFactory.getFolioS3Client(S3ConfigType.REINDEX);
    var second = FolioS3ClientFactory.getFolioS3Client(S3ConfigType.REINDEX);

    assertSame(first, second, "getFolioS3Client must return the cached instance on repeated calls");
  }

  @Test
  void differentInstancesReturnedForDifferentConfigTypes() {
    var reindex = FolioS3ClientFactory.getFolioS3Client(S3ConfigType.REINDEX);
    var marcMigration = FolioS3ClientFactory.getFolioS3Client(S3ConfigType.MARC_MIGRATION);

    assertNotSame(reindex, marcMigration, "Different config types must produce different client instances");
  }
}
