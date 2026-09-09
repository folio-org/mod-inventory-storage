package org.folio.rest.api;

import static org.folio.rest.impl.StorageHelper.POST_SYNC_MAX_ENTITIES_PROPERTY;
import static org.folio.utility.KafkaUtility.startKafka;
import static org.folio.utility.KafkaUtility.stopKafka;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.ModuleUtility.removeTenant;
import static org.folio.utility.ModuleUtility.startVerticleWebClientAndPrepareTenant;
import static org.folio.utility.ModuleUtility.stopVerticleAndWebClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.S3Utility.startS3;
import static org.folio.utility.S3Utility.stopS3;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

/**
 * Bootstraps Postgres, Kafka, S3 and the verticle once for the whole {@code rest.api} test run.
 *
 * <p>Every {@link TestBase} subclass calls {@link #startupUnlessRunning()} from its own
 * {@code @BeforeAll}, so this is started lazily by whichever test class runs first (idempotent,
 * guarded by {@link #running}) rather than by a dedicated suite runner class.
 */
public final class StorageTestSuite {
  private static final Logger logger = LogManager.getLogger();
  private static boolean running = false;

  private StorageTestSuite() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  @SneakyThrows
  static void before() {
    logger.info("starting before()");

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);
    System.setProperty("KAFKA_DOMAIN_TOPIC_NUM_PARTITIONS", "1");
    System.setProperty(POST_SYNC_MAX_ENTITIES_PROPERTY, "10");

    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    startKafka();
    startS3();

    startVerticleWebClientAndPrepareTenant(TENANT_ID);

    running = true;

    logger.info("finished before()");
  }

  static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    logger.info("starting after()");

    removeTenant(TENANT_ID);
    stopVerticleAndWebClient();
    stopKafka();
    stopS3();

    PostgresClient.stopPostgresTester();

    running = false;

    logger.info("finished after()");
  }

  @SneakyThrows
  private static void afterOnShutdown() {
    after();
  }

  /**
   * Setup Postgres, Kafka, Verticle if needed. To be used when directly running one or more test classes
   * (IDE or mvn test -Dtest=FooTest,BarTest), when not running the complete StorageTestSuite.
   */
  public static synchronized void startupUnlessRunning() {
    if (!running) {
      before();
      Runtime.getRuntime().addShutdownHook(new Thread(StorageTestSuite::afterOnShutdown));
    }
  }

  static void deleteAll(URL rootUrl) {
    deleteAll(rootUrl, TENANT_ID);
  }

  static void deleteAll(URL rootUrl, String tenantId) {
    HttpClient client = new HttpClient(getVertx());

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    try {
      client.delete(rootUrl + "?query=cql.allRecords=1", tenantId,
        ResponseHandler.any(deleteAllFinished));

      Response response = TestBase.get(deleteAllFinished);

      if (response.getStatusCode() != 204) {
        fail("Delete all preparation failed: " + response.getBody());
      }
    } catch (Exception e) {
      throw new RuntimeException("WARNING!!!!! Unable to delete all: " + e.getMessage(), e);
    }
  }

  static Boolean deleteAll(String tenantId, String tableName) {
    CompletableFuture<Boolean> cf = new CompletableFuture<>();

    try {
      PostgresClient.getInstance(getVertx(), tenantId)
        .execute(String.format("DELETE FROM %s_%s.%s", tenantId, "mod_inventory_storage", tableName))
        .map(deleteResult -> cf.complete(deleteResult.rowCount() >= 0))
        .otherwise(error -> cf.complete(false));

      return TestBase.get(cf);
    } catch (Exception e) {
      throw new RuntimeException("WARNING!!!!! Unable to delete all: " + e.getMessage(), e);
    }
  }

  static void checkForMismatchedIds(String table) {
    try {
      RowSet<Row> results = getRecordsWithUnmatchedIds(
        TENANT_ID, table);

      Integer mismatchedRowCount = results.rowCount();

      assertThat(mismatchedRowCount, is(0));
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new RuntimeException("WARNING!!!!! Unable to determine mismatched ID rows" + e.getMessage(), e);
    }
  }

  private static RowSet<Row> getRecordsWithUnmatchedIds(String tenantId, String tableName) {
    PostgresClient dbClient = PostgresClient.getInstance(getVertx(), tenantId);

    CompletableFuture<RowSet<Row>> selectCompleted = new CompletableFuture<>();

    String sql = String.format("SELECT null FROM %s_%s.%s WHERE CAST(id AS VARCHAR(50)) != jsonb->>'id'",
      tenantId, "mod_inventory_storage", tableName);

    dbClient.select(sql, result -> {
      if (result.succeeded()) {
        selectCompleted.complete(result.result());
      } else {
        selectCompleted.completeExceptionally(result.cause());
      }
    });

    return TestBase.get(selectCompleted);
  }
}
