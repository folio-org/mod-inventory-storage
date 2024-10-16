package org.folio.rest.support;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpClient {
  private static final Logger LOG = LogManager.getLogger();

  private static final String TENANT_HEADER = "X-Okapi-Tenant";
  private static final String X_OKAPI_URL = "X-Okapi-Url";
  private static final String X_OKAPI_URL_TO = "X-Okapi-Url-to";
  private static final String TOKEN_HEADER = "X-Okapi-Token";
  private static final String TEST_TOKEN = "test-token";

  private final WebClient client;

  public HttpClient(Vertx vertx) {
    client = WebClient.create(vertx);
  }

  public static CompletableFuture<Response> asResponse(Future<HttpResponse<Buffer>> future) {

    CompletableFuture<Response> completableFuture = new CompletableFuture<>();
    future
      .onSuccess(ResponseHandler.any(completableFuture))
      .onFailure(completableFuture::completeExceptionally);
    return completableFuture;
  }

  public WebClient getWebClient() {
    return client;
  }

  public Future<HttpResponse<Buffer>> request(
    HttpMethod method,
    URL url,
    Object body,
    String tenantId) {

    return request(method, url, body, Map.of(), tenantId);
  }

  public Future<HttpResponse<Buffer>> request(
    HttpMethod method,
    URL url,
    Object body,
    Map<String, String> headers,
    String tenantId) {

    try {
      HttpRequest<Buffer> request = client.requestAbs(method, url.toString());
      request.putHeader(CONTENT_TYPE, APPLICATION_JSON);
      addDefaultHeaders(request, url, tenantId);
      headers.entrySet().stream().forEach(header -> request.putHeader(header.getKey(),
        header.getValue()));

      if (body == null) {
        return request.send();
      }
      String encodedBody = Json.encodePrettily(body);
      LOG.info(format("%s %s, Request: %s", method.name(), url, encodedBody));
      return request.sendBuffer(Buffer.buffer(encodedBody));
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  public Future<HttpResponse<Buffer>> request(
    HttpMethod method,
    URL url,
    String tenantId) {

    try {
      HttpRequest<Buffer> request = client.requestAbs(method, url.toString());
      addDefaultHeaders(request, url, tenantId);
      return request.send();
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  /**
   * Warning: The responseHandler gets null on error, use
   * doPost(URL, Object, String) or {@link #post(URL, Object, String)}
   * for better error reporting.
   */
  public void post(
    URL url,
    Object body,
    Map<String, String> headers,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    request(HttpMethod.POST, url, body, headers, tenantId)
      .recover(error -> {
        LOG.error(error.getMessage(), error);
        return null;
      })
      .onSuccess(responseHandler);
  }

  /**
   * Warning: The responseHandler gets null on error, use
   * doPost(URL, Object, String) or {@link #post(URL, Object, String)}
   * for better error reporting.
   */
  public void post(
    URL url,
    Object body,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    post(url, body, Map.of(), tenantId, responseHandler);
  }

  public CompletableFuture<Response> post(URL url, Object body, String tenantId) {
    return asResponse(request(HttpMethod.POST, url, body, tenantId));
  }

  /**
   * Warning: The responseHandler gets null on error, use
   * doPut(URL, Object, String) or {@link #put(URL, Object, String)}
   * for better error reporting.
   */
  public void put(
    URL url,
    Object body,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    request(HttpMethod.PUT, url, body, tenantId)
      .recover(error -> {
        LOG.error(error.getMessage(), error);
        return null;
      })
      .onSuccess(responseHandler);
  }

  public void put(
    URL url,
    Object body,
    Map<String, String> headers,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    request(HttpMethod.PUT, url, body, headers, tenantId)
      .recover(error -> {
        LOG.error(error.getMessage(), error);
        return null;
      })
      .onSuccess(responseHandler);
  }

  public CompletableFuture<Response> put(URL url, Object body, String tenantId) {
    return asResponse(request(HttpMethod.PUT, url, body, tenantId));
  }

  public void get(
    String url,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    URL finalUrl = null;
    try {
      finalUrl = new URL(url);
    } catch (Exception e) {
      LOG.error(format("URL error: %s: %s", e.getMessage(), url), e);
    }
    get(finalUrl, tenantId, responseHandler);
  }

  /**
   * Warning: The responseHandler gets null on error, use
   * doGet(URL, String) or {@link #get(URL, String)}
   * for better error reporting.
   */
  public void get(
    URL url,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    request(HttpMethod.GET, url, tenantId)
      .recover(error -> {
        LOG.error(error.getMessage(), error);
        return null;
      })
      .onSuccess(responseHandler);
  }

  public CompletableFuture<Response> get(URL url, String tenantId) {
    return asResponse(request(HttpMethod.GET, url, tenantId));
  }

  /**
   * Warning: The responseHandler gets null on error, use
   * doDelete(URL, String) or {@link #delete(URL, String)}
   * for better error reporting.
   */
  public void delete(
    URL url,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    request(HttpMethod.DELETE, url, tenantId)
      .recover(error -> {
        LOG.error(error.getMessage(), error);
        return null;
      })
      .onSuccess(responseHandler);
  }

  public void delete(
    String url,
    String tenantId,
    Handler<HttpResponse<Buffer>> responseHandler) {

    URL finalUrl = null;
    try {
      finalUrl = new URL(url);
    } catch (Exception e) {
      LOG.info(format("URL error: %s: %s", e.getMessage(), url), e);
    }
    delete(finalUrl, tenantId, responseHandler);
  }

  public CompletableFuture<Response> delete(URL url, String tenantId) {
    return asResponse(request(HttpMethod.DELETE, url, tenantId));
  }

  public CompletableFuture<Response> patch(URL url, Object body, String tenantId) {
    return asResponse(request(HttpMethod.PATCH, url, body, tenantId));
  }

  private void addDefaultHeaders(HttpRequest<Buffer> request, URL url, String tenantId) {
    if (isNotBlank(tenantId)) {
      request.putHeader(TENANT_HEADER, tenantId);
      request.putHeader(TOKEN_HEADER, TEST_TOKEN);
    }
    if (url != null) {
      // FIXME: Several institutions have a Okapi URL with path, for example https://folio-demo.gbv.de/okapi
      // see https://github.com/folio-org/folio-ansible/blob/master/doc/index.md#replace-port-9130
      String baseUrl = format("%s://%s", url.getProtocol(), url.getAuthority());
      request.putHeader(X_OKAPI_URL, baseUrl);
      request.putHeader(X_OKAPI_URL_TO, baseUrl);
    }
    request.putHeader(ACCEPT, APPLICATION_JSON + ", " + TEXT_PLAIN);
  }
}
