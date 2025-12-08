package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locCampusStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locInstitutionStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locLibraryStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locationsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.materialTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUsersUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.fixtures.AsyncMigrationFixture;
import org.folio.rest.support.fixtures.InstanceReindexFixture;
import org.folio.rest.support.fixtures.StatisticalCodeFixture;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {
  /**
   * timeout in seconds for simple requests. Usage: completableFuture.get(TIMEOUT, TimeUnit.SECONDS)
   */
  public static final long TIMEOUT = 100;
  public static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  public static ResourceClient holdingsClient;
  protected static final Logger logger = LogManager.getLogger();
  protected static ResourceClient instancesClient;
  protected static ResourceClient itemsClient;
  protected static ResourceClient callNumberTypesClient;
  static final FakeKafkaConsumer KAFKA_CONSUMER = new FakeKafkaConsumer();
  static ResourceClient locationsClient;
  static ResourceClient modesOfIssuanceClient;
  static ResourceClient precedingSucceedingTitleClient;
  static ResourceClient instanceRelationshipsClient;
  static ResourceClient instanceRelationshipTypesClient;
  static ResourceClient instancesStorageSyncClient;
  static ResourceClient itemsStorageSyncClient;
  static ResourceClient instanceTypesClient;
  static ResourceClient illPoliciesClient;
  static ResourceClient inventoryViewClient;
  static ResourceClient statisticalCodeClient;
  static ResourceClient boundWithClient;
  static ResourceClient holdingsSourceClient;
  static ResourceClient servicePointsClient;
  static StatisticalCodeFixture statisticalCodeFixture;
  static InstanceReindexFixture instanceReindex;
  static AsyncMigrationFixture asyncMigration;

  @BeforeClass
  public static void beforeAll() {
    logger.info("starting @BeforeClass testBaseBeforeClass()");

    StorageTestSuite.startupUnlessRunning();

    initializeResourceClients();

    statisticalCodeFixture = new StatisticalCodeFixture(getClient());
    instanceReindex = new InstanceReindexFixture(getClient());
    asyncMigration = new AsyncMigrationFixture(getClient());

    KAFKA_CONSUMER.discardAllMessages();
    KAFKA_CONSUMER.consume(getVertx());

    logger.info("finishing @BeforeClass testBaseBeforeClass()");
  }

  private static void initializeResourceClients() {
    instancesClient = ResourceClient.forInstances(getClient());
    holdingsClient = ResourceClient.forHoldings(getClient());
    itemsClient = ResourceClient.forItems(getClient());
    locationsClient = ResourceClient.forLocations(getClient());
    callNumberTypesClient = ResourceClient.forCallNumberTypes(getClient());
    modesOfIssuanceClient = ResourceClient.forModesOfIssuance(getClient());
    instanceRelationshipsClient = ResourceClient.forInstanceRelationships(getClient());
    instanceRelationshipTypesClient = ResourceClient.forInstanceRelationshipTypes(getClient());
    precedingSucceedingTitleClient = ResourceClient.forPrecedingSucceedingTitles(getClient());
    instancesStorageSyncClient = ResourceClient.forInstancesStorageSync(getClient());
    itemsStorageSyncClient = ResourceClient.forItemsStorageSync(getClient());
    inventoryViewClient = ResourceClient.forInventoryView(getClient());
    statisticalCodeClient = ResourceClient.forStatisticalCodes(getClient());
    boundWithClient = ResourceClient.forBoundWithParts(getClient());
    holdingsSourceClient = ResourceClient.forHoldingsSource(getClient());
    instanceTypesClient = ResourceClient.forInstanceTypes(getClient());
    illPoliciesClient = ResourceClient.forIllPolicies(getClient());
    servicePointsClient = ResourceClient.forServicePoints(getClient());
  }

  @AfterClass
  public static void afterAll() {
    KAFKA_CONSUMER.unsubscribe();
  }

  /**
   * Clear as much data as is safely possible.
   *
   * <p>The intended use of this function is to clear as much data as possible
   * with the goal of maintaining isolated states between tests.
   *
   * <p>This does not clear all possible data due to observed problems with
   * several tests. Through rigorous testing the data cleared here has been
   * found to work reasonably well across all tests. Clearing some data, such
   * as with StorageTestSuite.deleteAll(authoritiesStorageUrl("")) has been
   * found to not work across all tests when added to this function. These
   * problematic data clearing sets are located within the tests that need
   * them rather than in this function.
   *
   * <p>Once all tests are properly implemented to safely work with completely
   * isolated starting states, then this should be updated or removed
   * accordingly.
   */
  protected static void clearData() {
    clearData(TENANT_ID);
  }

  protected static void clearData(String tenantId) {
    StorageTestSuite.deleteAll(itemsStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(holdingsStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(instancesStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(locationsStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(locCampusStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(materialTypesStorageUrl(""), tenantId);
    StorageTestSuite.deleteAll(servicePointsUsersUrl(""), tenantId);
    StorageTestSuite.deleteAll(servicePointsUrl(""), tenantId);
  }

  /**
   * Delete all rows found using the client by "id" field.
   */
  protected static void deleteAllById(ResourceClient client) {
    for (JsonObject row : client.getAll()) {
      client.delete(UUID.fromString(row.getString("id")));
    }
  }

  /**
   * Returns future.get({@link #TIMEOUT}, {@link TimeUnit#SECONDS}).
   *
   * <p>Wraps these checked exceptions into RuntimeException:
   * InterruptedException, ExecutionException, TimeoutException.
   */
  public static <T> T get(CompletableFuture<T> future) {
    try {
      return future.get(TestBase.TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns future.get({@link #TIMEOUT}, {@link TimeUnit#SECONDS}).
   *
   * <p>Wraps these checked exceptions into RuntimeException:
   * InterruptedException, ExecutionException, TimeoutException.
   */
  public static <T> T get(Future<T> future) {
    return get(future.toCompletionStage().toCompletableFuture());
  }

  @SneakyThrows
  @Before
  public void removeAllEvents() {
    KAFKA_CONSUMER.discardAllMessages();
  }

  /**
   * Assert that a GET at the url returns 404 status code (= not found).
   *
   * @param url endpoint where to execute a GET request
   */
  void assertGetNotFound(URL url) {
    assertGetNotFound(url, TENANT_ID);
  }

  void assertGetNotFound(URL url, String tenantId) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(url, tenantId, ResponseHandler.text(getCompleted));
    Response response = get(getCompleted);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  public static JsonObject pojo2JsonObject(Object entity) {
    try {
      return PostgresClient.pojo2JsonObject(entity);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  protected static UUID getPreparedHoldingSourceId() {
    UUID sourceId = UUID.randomUUID();
    holdingsSourceClient.create(new JsonObject()
      .put("id", sourceId.toString())
      .put("name", "holding source name for " + sourceId));
    return sourceId;
  }
}
