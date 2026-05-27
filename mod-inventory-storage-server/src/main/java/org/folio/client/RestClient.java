package org.folio.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.util.Map;

public interface RestClient {

  Future<HttpResponse<Buffer>> get(String path, Map<String, String> headers);

  Future<HttpResponse<Buffer>> get(String path, Map<String, String> headers, int expectedStatus);

  <T> Future<T> get(String path, Map<String, String> headers, Class<T> responseType);

  <T> Future<T> get(String path, Map<String, String> headers, int expectedStatus, Class<T> responseType);

  Future<HttpResponse<Buffer>> post(String path, Map<String, String> headers, Object body);

  Future<HttpResponse<Buffer>> post(String path, Map<String, String> headers, Object body, int expectedStatus);

  <T> Future<T> post(String path, Map<String, String> headers, Object body, Class<T> responseType);

  <T> Future<T> post(String path, Map<String, String> headers, Object body, int expectedStatus, Class<T> responseType);

  Future<HttpResponse<Buffer>> put(String path, Map<String, String> headers, Object body);

  Future<HttpResponse<Buffer>> put(String path, Map<String, String> headers, Object body, int expectedStatus);

  <T> Future<T> put(String path, Map<String, String> headers, Object body, Class<T> responseType);

  <T> Future<T> put(String path, Map<String, String> headers, Object body, int expectedStatus, Class<T> responseType);

  Future<HttpResponse<Buffer>> delete(String path, Map<String, String> headers);

  Future<HttpResponse<Buffer>> delete(String path, Map<String, String> headers, int expectedStatus);
}
