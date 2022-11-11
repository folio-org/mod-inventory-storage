package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.getClient;
import static org.folio.rest.api.StorageTestSuite.getVertx;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
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
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.fixtures.AsyncMigrationFixture;
import org.folio.rest.support.fixtures.AuthorityReindexFixture;
import org.folio.rest.support.fixtures.InstanceReindexFixture;
import org.folio.rest.support.fixtures.StatisticalCodeFixture;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
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

  protected static ResourceClient instancesClient;
  public static ResourceClient holdingsClient;
  protected static ResourceClient itemsClient;
  protected static ResourceClient authoritiesClient;
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
  static ResourceClient statisticalCodeClient;
  static StatisticalCodeFixture statisticalCodeFixture;
  static FakeKafkaConsumer kafkaConsumer;
  static InstanceReindexFixture instanceReindex;
  static AuthorityReindexFixture authorityReindex;
  static AsyncMigrationFixture asyncMigration;

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

    instancesClient = ResourceClient.forInstances(getClient());
    holdingsClient = ResourceClient.forHoldings(getClient());
    itemsClient = ResourceClient.forItems(getClient());
    authoritiesClient = ResourceClient.forAuthorities(getClient());
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
    instancesStorageBatchInstancesClient = ResourceClient
      .forInstancesStorageBatchInstances(getClient());
    instanceTypesClient = ResourceClient
      .forInstanceTypes(getClient());
    illPoliciesClient = ResourceClient.forIllPolicies(getClient());
    statisticalCodeFixture = new StatisticalCodeFixture(getClient());
    kafkaConsumer = new FakeKafkaConsumer().consume(getVertx());
    FakeKafkaConsumer.removeAllEvents();
    instanceReindex = new InstanceReindexFixture(getClient());
    authorityReindex = new AuthorityReindexFixture(getClient());
    asyncMigration = new AsyncMigrationFixture(getClient());
    logger.info("finishing @BeforeClass testBaseBeforeClass()");
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
    getClient().getWebClient()
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

    getClient().get(url, TENANT_ID, ResponseHandler.text(getCompleted));
    Response response = get(getCompleted);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

}
