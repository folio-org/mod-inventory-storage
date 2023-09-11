package org.folio.utility;

import static java.lang.String.format;
import static org.folio.utility.ModuleUtility.getClient;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import java.net.URL;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RestUtility {
  public static final String TENANT_ID = "test_tenant";

  private static final Logger logger = LogManager.getLogger();

  private RestUtility() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static void send(URL url, HttpMethod method, String content,
                          String contentType, Handler<HttpResponse<Buffer>> handler) {

    send(url, method, null, content, contentType, handler);
  }

  public static void send(URL url, HttpMethod method, String userId, String content,
                          String contentType, Handler<HttpResponse<Buffer>> handler) {

    send(url.toString(), method, userId, content, contentType, handler);
  }

  public static Future<HttpResponse<Buffer>> send(URL url, HttpMethod method, String content,
                                                  String contentType) {

    return Future.future(promise -> send(url, method, content, contentType, promise::complete));
  }

  public static void send(String url, HttpMethod method, String content,
                          String contentType, Handler<HttpResponse<Buffer>> handler) {

    send(url, method, null, content, contentType, handler);
  }

  public static void send(String url, HttpMethod method, String userId, String content,
                          String contentType, Handler<HttpResponse<Buffer>> handler) {

    Buffer body = Buffer.buffer(content == null ? "" : content);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    if (userId != null) {
      headers.add("X-Okapi-User-Id", userId);
    }

    getClient().getWebClient()
      .requestAbs(method, url)
      .putHeader("Authorization", TENANT_ID)
      .putHeader("x-okapi-tenant", TENANT_ID)
      .putHeader("Accept", "application/json,text/plain")
      .putHeader("Content-type", contentType)
      .putHeaders(headers)
      .sendBuffer(body)
      .onSuccess(handler)
      .onFailure(error -> logger.error(error.getMessage(), error));
  }

  public static void send(URL url, HttpMethod method, String content,
    Handler<HttpResponse<Buffer>> handler) {

    Buffer body = Buffer.buffer(content == null ? "" : content);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    String baseUrl = format("%s://%s", url.getProtocol(), url.getAuthority());

    getClient().getWebClient()
      .requestAbs(method, url.toString())
      .putHeader("Authorization", TENANT_ID)
      .putHeader("X-Okapi-Tenant", TENANT_ID)
      .putHeader("X-Okapi-Url-to", baseUrl)
      .putHeader("X-Okapi-Url", baseUrl)
      .putHeader("Accept", "application/json,text/plain")
      .putHeader("Content-type", "application/json")
      .putHeaders(headers)
      .sendBuffer(body)
      .onSuccess(handler)
      .onFailure(error -> logger.error(error.getMessage(), error));
  }
}
