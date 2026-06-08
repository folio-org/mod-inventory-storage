package org.folio.client;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.okapi.common.XOkapiHeaders.URL;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.Map;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RestClientImpl implements RestClient {

  private static final Logger LOG = LogManager.getLogger(RestClientImpl.class);

  private final WebClient webClient;

  public RestClientImpl(HttpClient httpClient) {
    this.webClient = WebClient.wrap(httpClient);
  }

  @Override
  public Future<HttpResponse<Buffer>> get(String path, Map<String, String> headers) {
    return get(path, headers, HTTP_OK.toInt());
  }

  @Override
  public Future<HttpResponse<Buffer>> get(String path, Map<String, String> headers, int expectedStatus) {
    return doRequest(HttpMethod.GET, path, headers, null)
      .compose(response -> validateStatus(response, expectedStatus));
  }

  @Override
  public <T> Future<T> get(String path, Map<String, String> headers, Class<T> responseType) {
    return get(path, headers, HTTP_OK.toInt(), responseType);
  }

  @Override
  public <T> Future<T> get(String path, Map<String, String> headers, int expectedStatus, Class<T> responseType) {
    return get(path, headers, expectedStatus).map(response -> response.bodyAsJson(responseType));
  }

  @Override
  public Future<HttpResponse<Buffer>> post(String path, Map<String, String> headers, Object body) {
    return post(path, headers, body, HTTP_CREATED.toInt());
  }

  @Override
  public Future<HttpResponse<Buffer>> post(String path, Map<String, String> headers, Object body, int expectedStatus) {
    return doRequest(HttpMethod.POST, path, headers, body)
      .compose(response -> validateStatus(response, expectedStatus));
  }

  @Override
  public <T> Future<T> post(String path, Map<String, String> headers, Object body, Class<T> responseType) {
    return post(path, headers, body, HTTP_CREATED.toInt(), responseType);
  }

  @Override
  public <T> Future<T> post(String path, Map<String, String> headers, Object body, int expectedStatus,
                             Class<T> responseType) {
    return post(path, headers, body, expectedStatus)
      .map(response -> response.bodyAsJson(responseType));
  }

  @Override
  public Future<HttpResponse<Buffer>> put(String path, Map<String, String> headers, Object body) {
    return put(path, headers, body, HTTP_NO_CONTENT.toInt());
  }

  @Override
  public Future<HttpResponse<Buffer>> put(String path, Map<String, String> headers, Object body, int expectedStatus) {
    return doRequest(HttpMethod.PUT, path, headers, body)
      .compose(response -> validateStatus(response, expectedStatus));
  }

  @Override
  public <T> Future<T> put(String path, Map<String, String> headers, Object body, Class<T> responseType) {
    return put(path, headers, body, HTTP_NO_CONTENT.toInt(), responseType);
  }

  @Override
  public <T> Future<T> put(String path, Map<String, String> headers, Object body, int expectedStatus,
                            Class<T> responseType) {
    return put(path, headers, body, expectedStatus)
      .map(response -> response.bodyAsJson(responseType));
  }

  @Override
  public Future<HttpResponse<Buffer>> delete(String path, Map<String, String> headers) {
    return delete(path, headers, HTTP_NO_CONTENT.toInt());
  }

  @Override
  public Future<HttpResponse<Buffer>> delete(String path, Map<String, String> headers, int expectedStatus) {
    return doRequest(HttpMethod.DELETE, path, headers, null)
      .compose(response -> validateStatus(response, expectedStatus));
  }

  private Future<HttpResponse<Buffer>> doRequest(HttpMethod method, String path,
                                                  Map<String, String> headers, Object body) {
    var caseInsensitiveHeaders = new CaseInsensitiveMap<>(headers);
    String okapiUrl = caseInsensitiveHeaders.get(URL);
    if (okapiUrl == null) {
      LOG.error("doRequest:: {} {} - Okapi URL header is missing", method, path);
      return Future.failedFuture("Okapi URL is not specified in headers");
    }

    var request = webClient.requestAbs(method, okapiUrl + path);
    caseInsensitiveHeaders.forEach(request::putHeader);

    LOG.info("doRequest:: {} {}", method, okapiUrl + path);

    return (body == null ? request.send() : request.sendJson(body))
      .onFailure(e -> LOG.error("doRequest:: {} {} failed", method, okapiUrl + path, e));
  }

  private Future<HttpResponse<Buffer>> validateStatus(HttpResponse<Buffer> response, int expectedStatus) {
    if (response.statusCode() != expectedStatus) {
      String message = "Expected status %d but got %d, body: %s"
        .formatted(expectedStatus, response.statusCode(), response.bodyAsString());
      LOG.warn("validateStatus:: {}", message);
      return Future.failedFuture(message);
    }
    return Future.succeededFuture(response);
  }
}
