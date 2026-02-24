package org.folio.utility;

import static java.time.Duration.ofMinutes;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

public final class S3Utility {

  public static final String MARC_MIGRATION_BUCKET = "marc-migration-bucket";
  public static final String REINDEX_BUCKET = "reindex-bucket";

  private static final Logger logger = LogManager.getLogger();

  private static final LocalStackContainer S3_CONTAINER =
    new LocalStackContainer(DockerImageName.parse("localstack/localstack:s3-latest"))
      .withServices("s3");

  private S3Utility() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static void startS3() {
    S3_CONTAINER.start();
    logger.info("starting Kafka host={} port={}", S3_CONTAINER.getHost(), S3_CONTAINER.getFirstMappedPort());

    System.setProperty("S3_MARC_MIGRATION_URL", S3_CONTAINER.getEndpoint().toString());
    System.setProperty("S3_MARC_MIGRATION_REGION", S3_CONTAINER.getRegion());
    System.setProperty("S3_MARC_MIGRATION_ACCESS_KEY_ID", S3_CONTAINER.getAccessKey());
    System.setProperty("S3_MARC_MIGRATION_SECRET_ACCESS_KEY", S3_CONTAINER.getSecretKey());
    System.setProperty("S3_MARC_MIGRATION_BUCKET", MARC_MIGRATION_BUCKET);
    System.setProperty("S3_MARC_MIGRATION_IS_AWS", Boolean.FALSE.toString());

    System.setProperty("S3_REINDEX_URL", S3_CONTAINER.getEndpoint().toString());
    System.setProperty("S3_REINDEX_REGION", S3_CONTAINER.getRegion());
    System.setProperty("S3_REINDEX_ACCESS_KEY_ID", S3_CONTAINER.getAccessKey());
    System.setProperty("S3_REINDEX_SECRET_ACCESS_KEY", S3_CONTAINER.getSecretKey());
    System.setProperty("S3_REINDEX_BUCKET", REINDEX_BUCKET);
    System.setProperty("S3_REINDEX_IS_AWS", Boolean.FALSE.toString());

    await().atMost(ofMinutes(1)).until(S3_CONTAINER::isRunning);

    logger.info("finished starting S3");
  }

  public static void stopS3() {
    if (S3_CONTAINER.isRunning()) {
      logger.info("stopping S3 host={} port={}", S3_CONTAINER.getHost(), S3_CONTAINER.getFirstMappedPort());

      S3_CONTAINER.stop();
      logger.info("finished stopping S3");
    } else {
      logger.info("S3 container already stopped");
    }
  }
}
