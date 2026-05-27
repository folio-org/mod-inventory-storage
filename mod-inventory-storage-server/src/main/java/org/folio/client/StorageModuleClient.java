package org.folio.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.util.Map;

public interface StorageModuleClient<T> {

  Future<HttpResponse<Buffer>> getAll(Map<String, String> headers);

  Future<HttpResponse<Buffer>> getById(String id, Map<String, String> headers);

  Future<HttpResponse<Buffer>> create(T item, Map<String, String> headers);

  Future<HttpResponse<Buffer>> update(String id, T item, Map<String, String> headers);

  Future<HttpResponse<Buffer>> delete(String id, Map<String, String> headers);
}
