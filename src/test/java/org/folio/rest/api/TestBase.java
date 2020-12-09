package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.fixtures.StatisticalCodeFixture;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.consol.citrus.kafka.embedded.EmbeddedKafkaServer;
import com.consol.citrus.kafka.embedded.EmbeddedKafkaServerBuilder;

import io.vertx.core.Vertx;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {
  /** timeout in seconds for simple requests. Usage: completableFuture.get(TIMEOUT, TimeUnit.SECONDS) */
  public static final long TIMEOUT = 10;

  private static boolean invokeStorageTestSuiteAfter = false;
  static HttpClient client;
  protected static ResourceClient instancesClient;
  protected static ResourceClient holdingsClient;
  protected static ResourceClient itemsClient;
  static ResourceClient locationsClient;
  static ResourceClient callNumberTypesClient;
  static ResourceClient modesOfIssuanceClient;
  static ResourceClient precedingSucceedingTitleClient;
  static ResourceClient instanceRelationshipsClient;
  static ResourceClient instanceRelationshipTypesClient;
  static ResourceClient instancesStorageSyncClient;
  static ResourceClient itemsStorageSyncClient;
  static ResourceClient instancesStorageBatchInstancesClient;
  static ResourceClient instanceTypesClient;
  static ResourceClient illPoliciesClient;
  static StatisticalCodeFixture statisticalCodeFixture;
  static FakeKafkaConsumer kafkaConsumer;

  private static final EmbeddedKafkaServer kafka = new EmbeddedKafkaServerBuilder()
    .kafkaServerPort(9092).build();

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

  @BeforeClass
  public static void testBaseBeforeClass() throws Exception {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      kafka.start();
      StorageTestSuite.before();
      vertx = StorageTestSuite.getVertx();
    }

    client = new HttpClient(vertx);
    instancesClient = ResourceClient.forInstances(client);
    holdingsClient = ResourceClient.forHoldings(client);
    itemsClient = ResourceClient.forItems(client);
    locationsClient = ResourceClient.forLocations(client);
    callNumberTypesClient = ResourceClient.forCallNumberTypes(client);
    modesOfIssuanceClient = ResourceClient.forModesOfIssuance(client);
    instanceRelationshipsClient = ResourceClient.forInstanceRelationships(client);
    instanceRelationshipTypesClient = ResourceClient.forInstanceRelationshipTypes(client);
    precedingSucceedingTitleClient = ResourceClient.forPrecedingSucceedingTitles(client);
    instancesStorageSyncClient = ResourceClient.forInstancesStorageSync(client);
    itemsStorageSyncClient = ResourceClient.forItemsStorageSync(client);
    instancesStorageBatchInstancesClient = ResourceClient
      .forInstancesStorageBatchInstances(client);
    instanceTypesClient = ResourceClient
      .forInstanceTypes(client);
    illPoliciesClient = ResourceClient.forIllPolicies(client);
    statisticalCodeFixture = new StatisticalCodeFixture(client);
    kafkaConsumer = new FakeKafkaConsumer().consume(vertx);
  }

  @AfterClass
  public static void testBaseAfterClass()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
      kafka.stop();
    }
  }

  @Before
  public void removeKafkaMessages() {
    kafkaConsumer.removeAllMessages();
  }

  /**
   * Assert that a GET at the url returns 404 status code (= not found).
   * @param url  endpoint where to execute a GET request
   */
  void assertGetNotFound(URL url) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(url, TENANT_ID, ResponseHandler.text(getCompleted));
    Response response = get(getCompleted);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

}
