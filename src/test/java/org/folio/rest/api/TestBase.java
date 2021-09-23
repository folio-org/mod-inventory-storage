package org.folio.rest.api;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.fixtures.InstanceReindexFixture;
import org.folio.rest.support.fixtures.StatisticalCodeFixture;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {
  protected static final Logger logger = LogManager.getLogger();
  /** timeout in seconds for simple requests. Usage: completableFuture.get(TIMEOUT, TimeUnit.SECONDS) */
  public static final long TIMEOUT = 10;

  private static boolean invokeStorageTestSuiteAfter = false;
  static HttpClient client;
  protected static ResourceClient instancesClient;
  public static ResourceClient holdingsClient;
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
  static ResourceClient inventoryViewClient;
  static StatisticalCodeFixture statisticalCodeFixture;
  static FakeKafkaConsumer kafkaConsumer;
  static InstanceReindexFixture instanceReindex;

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

  @BeforeClass
  public static void testBaseBeforeClass() {
    logger.info("starting @BeforeClass testBaseBeforeClass()");
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
      vertx = StorageTestSuite.getVertx();
    } else {
      invokeStorageTestSuiteAfter = false;
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
    inventoryViewClient = ResourceClient.forInventoryView(client);
    instancesStorageBatchInstancesClient = ResourceClient
      .forInstancesStorageBatchInstances(client);
    instanceTypesClient = ResourceClient
      .forInstanceTypes(client);
    illPoliciesClient = ResourceClient.forIllPolicies(client);
    statisticalCodeFixture = new StatisticalCodeFixture(client);
    kafkaConsumer = new FakeKafkaConsumer().consume(vertx);
    kafkaConsumer.removeAllEvents();
    instanceReindex = new InstanceReindexFixture(client);
    logger.info("finishing @BeforeClass testBaseBeforeClass()");
  }

  @AfterClass
  public static void testBaseAfterClass()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    client.getWebClient().close();
    client = null;
    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }

  static void send(URL url, HttpMethod method, String content,
      String contentType, Handler<HttpResponse<Buffer>> handler) {
    send(url, method, null, content, contentType, handler);
  }

  static void send(URL url, HttpMethod method, String userId, String content,
                   String contentType, Handler<HttpResponse<Buffer>> handler) {
    send(url.toString(), method, userId, content, contentType, handler);
  }

  static Future<HttpResponse<Buffer>> send(URL url, HttpMethod method, String content,
      String contentType) {
    return Future.future(promise -> send(url, method, content, contentType, promise::complete));
  }

  static void send(String url, HttpMethod method, String content,
                   String contentType, Handler<HttpResponse<Buffer>> handler) {
    send(url, method, null, content, contentType, handler);
  }

  static void send(String url, HttpMethod method, String userId, String content,
        String contentType, Handler<HttpResponse<Buffer>> handler) {
    Buffer body = Buffer.buffer(content == null ? "" : content);

    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    if (userId != null) {
      headers.add("X-Okapi-User-Id", userId);
    }
    client.getWebClient()
    .requestAbs(method, url)
    .putHeader("Authorization", "test_tenant")
    .putHeader("x-okapi-tenant", "test_tenant")
    .putHeader("Accept", "application/json,text/plain")
    .putHeader("Content-type", contentType)
    .putHeaders(headers)
    .sendBuffer(body)
    .onSuccess(handler)
    .onFailure(error -> logger.error(error.getMessage(), error));
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
