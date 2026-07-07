package org.folio.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import org.folio.rest.jaxrs.model.ServicePoint;

public class ServicePointClient implements StorageModuleClient<ServicePoint> {

  private static final String BASE_PATH = "/service-point-storage/service-points";

  private final RestClient restClient;

  public ServicePointClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public Future<HttpResponse<Buffer>> getAll(Map<String, String> headers) {
    return restClient.get(BASE_PATH, headers);
  }

  @Override
  public Future<HttpResponse<Buffer>> getById(String id, Map<String, String> headers) {
    return restClient.get(BASE_PATH + "/" + id, headers);
  }

  @Override
  public Future<HttpResponse<Buffer>> create(ServicePoint item, Map<String, String> headers) {
    return restClient.post(BASE_PATH, headers, item);
  }

  @Override
  public Future<HttpResponse<Buffer>> update(String id, ServicePoint item, Map<String, String> headers) {
    return restClient.put(BASE_PATH + "/" + id, headers, item);
  }

  @Override
  public Future<HttpResponse<Buffer>> delete(String id, Map<String, String> headers) {
    return restClient.delete(BASE_PATH + "/" + id, headers);
  }

  public Future<HttpResponse<Buffer>> getByQuery(String cql, int offset, int limit, boolean includeRoutingServicePoints,
                                                 Map<String, String> headers) {
    return restClient.get(buildQueryPath(cql, offset, limit, includeRoutingServicePoints), headers);
  }

  private String buildQueryPath(String cql, int offset, int limit, boolean includeRoutingServicePoints) {
    var params = new StringJoiner("&", BASE_PATH + "?", "");
    params.add("offset=" + offset);
    params.add("limit=" + limit);
    if (cql != null && !cql.isBlank()) {
      params.add("query=" + URLEncoder.encode(cql, StandardCharsets.UTF_8));
    }
    params.add("includeRoutingServicePoints=" + includeRoutingServicePoints);
    return params.toString();
  }
}
