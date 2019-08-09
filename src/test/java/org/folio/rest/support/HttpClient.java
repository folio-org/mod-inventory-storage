package org.folio.rest.support;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpClient {
  private static final Logger LOG = LoggerFactory.getLogger(HttpClient.class);

  private static final String TENANT_HEADER = "X-Okapi-Tenant";
  private static final String X_OKAPI_URL = "X-Okapi-Url";
  private static final String X_OKAPI_URL_TO = "X-Okapi-Url-to";

  private final io.vertx.core.http.HttpClient client;

  public HttpClient(Vertx vertx) {
    client = vertx.createHttpClient();
  }

  private void addDefaultHeaders(HttpClientRequest request, String url, String tenantId) {
    try {
      addDefaultHeaders(request, new URL(url), tenantId);
    } catch (MalformedURLException ex) {
      LOG.info(format("Malformed url: %s, message: %s", url, ex.getMessage()));
    }
  }

  private void addDefaultHeaders(HttpClientRequest request, URL url, String tenantId) {
    if (isNotBlank(tenantId)) {
      request.headers().add(TENANT_HEADER, tenantId);
    }
    if (url != null) {
      String baseUrl = format("%s://%s", url.getProtocol(), url.getAuthority());
      request.headers().add(X_OKAPI_URL, baseUrl);
      request.headers().add(X_OKAPI_URL_TO, baseUrl);
    }
    request.headers().add(ACCEPT, APPLICATION_JSON + ", " + TEXT_PLAIN);
  }

  public void post(
    URL url,
    Object body,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.postAbs(url.toString(), responseHandler);
    request.putHeader(CONTENT_TYPE, APPLICATION_JSON);
    addDefaultHeaders(request, url, tenantId);

    if (body == null) {
      request.end();
    } else {
      String encodedBody = Json.encodePrettily(body);
      LOG.info(format("POST %s, Request: %s", url.toString(), encodedBody));
      request.end(encodedBody);
    }
  }

  public CompletableFuture<Response> post(URL url, Object body, String tenantId) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    post(url, body, tenantId, ResponseHandler.any(future));
    return future;
  }

  public void put(
    URL url,
    Object body,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.putAbs(url.toString(), responseHandler);

    request.putHeader(CONTENT_TYPE, APPLICATION_JSON);
    addDefaultHeaders(request, url, tenantId);

    String encodedBody = Json.encodePrettily(body);
    LOG.info(format("PUT %s, Request: %s", url.toString(), encodedBody));
    request.end(encodedBody);
  }

  public CompletableFuture<Response> put(URL url, Object body, String tenantId) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    put(url, body, tenantId, ResponseHandler.any(future));
    return future;
  }

  public void get(
    URL url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    get(url.toString(), tenantId, responseHandler);
  }

  public void get(
    String url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.getAbs(url, responseHandler);
    addDefaultHeaders(request, url, tenantId);
    request.end();
  }

  public CompletableFuture<Response> get(URL url, String tenantId) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    get(url, tenantId, ResponseHandler.any(future));
    return future;
  }

  public void delete(
    URL url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    delete(url.toString(), tenantId, responseHandler);
  }

  public void delete(
    String url,
    String tenantId,
    Handler<HttpClientResponse> responseHandler) {

    HttpClientRequest request = client.deleteAbs(url, responseHandler);
    addDefaultHeaders(request, url, tenantId);
    request.end();
  }

  public CompletableFuture<Response> delete(URL url, String tenantId) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    delete(url, tenantId, ResponseHandler.any(future));
    return future;
  }
}
