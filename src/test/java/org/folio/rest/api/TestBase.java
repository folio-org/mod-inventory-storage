package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;

import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.http.ResourceClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.google.common.base.Strings;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {
  private static boolean invokeStorageTestSuiteAfter = false;
  static HttpClient client;
  static ResourceClient instancesClient;
  static ResourceClient holdingsClient;

  @BeforeClass
  public static void testBaseBeforeClass() throws Exception {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
      vertx = StorageTestSuite.getVertx();
    }

    client = new HttpClient(vertx);
    instancesClient = ResourceClient.forInstances(client);
    holdingsClient = ResourceClient.forHoldings(client);
  }

  @AfterClass
  public static void testBaseAfterClass()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }

  /**
   * It sends a request to an endpoint.
   * It validates a response with expected status.
   * If response has body in application/json format then it returns a JsonObject, otherwise null.
   *
   * @param url                an URL of endpoint
   * @param method             a http method
   * @param content            a body content (can be null)
   * @param expectedStatusCode an expected status
   * @return a response from an endpoint
   */
  protected JsonObject send(
    @Nonnull URL url,
    @Nonnull HttpMethod method,
    @Nullable String content,
    @Nonnull int expectedStatusCode) {

    CompletableFuture<Response> future = new CompletableFuture<>();
    send(url, method, content, ResponseHandler.any(future));
    Response response = getResponse(future, 5);

    String reason = url + " - " + method + " - " + content + ":" + response.getBody();
    assertThat(reason, response.getStatusCode(), is(expectedStatusCode));

    return response.isJsonContent() ? response.getJson() : null;
  }

  /**
   * It creates a request based on an URL, http method and body content (if exists).
   * Also it adds a default exception handler that just fails a test in case of
   * any exception and puts http headers.
   *
   * @param url     an URL of an endpoint.
   * @param method  a http method
   * @param content a body content (can be null)
   * @param handler a response handler.
   */
  protected void send(
    @Nonnull URL url,
    @Nonnull HttpMethod method,
    @Nullable String content,
    @Nonnull Handler<HttpClientResponse> handler) {

    io.vertx.core.http.HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request = getHttpClientRequest(url, method, client);
    request.exceptionHandler(error -> fail(error.getLocalizedMessage())).handler(handler);
    request.putHeader("Authorization", "test_tenant");
    request.putHeader("x-okapi-tenant", "test_tenant");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", MediaType.APPLICATION_JSON);
    request.end(Buffer.buffer(Strings.nullToEmpty(content)));
  }

  private HttpClientRequest getHttpClientRequest(
    URL url,
    HttpMethod method,
    io.vertx.core.http.HttpClient client) {

    HttpClientRequest request;
    if (method == HttpMethod.POST) {
      request = client.postAbs(url.toString());
    } else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url.toString());
    } else if (method == HttpMethod.GET) {
      request = client.getAbs(url.toString());
    } else {
      request = client.putAbs(url.toString());
    }
    return request;
  }

  private Response getResponse(CompletableFuture<Response> future, long timeout) {
    try {
      return future.get(timeout, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }
  }
}
