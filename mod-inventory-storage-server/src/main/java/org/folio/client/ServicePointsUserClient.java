package org.folio.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import org.folio.rest.jaxrs.model.ServicePointsUser;

public class ServicePointsUserClient implements StorageModuleClient<ServicePointsUser> {

  private static final String BASE_PATH = "/service-point-storage/service-points-users";

  private final RestClient restClient;

  public ServicePointsUserClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public Future<HttpResponse<Buffer>> getAll(Map<String, String> headers) {
    return restClient.get(BASE_PATH, headers);
  }

  public Future<HttpResponse<Buffer>> getByQuery(String cql, int offset, int limit, String totalRecords,
                                                 Map<String, String> headers) {
    return restClient.get(buildQueryPath(cql, offset, limit, totalRecords), headers);
  }

  private String buildQueryPath(String cql, int offset, int limit, String totalRecords) {
    var params = new StringJoiner("&", BASE_PATH + "?", "");
    params.add("offset=" + offset);
    params.add("limit=" + limit);
    if (cql != null && !cql.isBlank()) {
      params.add("query=" + URLEncoder.encode(cql, StandardCharsets.UTF_8));
    }
    if (totalRecords != null && !totalRecords.isBlank()) {
      params.add("totalRecords=" + totalRecords);
    }
    return params.toString();
  }

  @Override
  public Future<HttpResponse<Buffer>> getById(String id, Map<String, String> headers) {
    return restClient.get(BASE_PATH + "/" + id, headers);
  }

  @Override
  public Future<HttpResponse<Buffer>> create(ServicePointsUser item, Map<String, String> headers) {
    return restClient.post(BASE_PATH, headers, item);
  }

  @Override
  public Future<HttpResponse<Buffer>> update(String id, ServicePointsUser item, Map<String, String> headers) {
    return restClient.put(BASE_PATH + "/" + id, headers, item);
  }

  @Override
  public Future<HttpResponse<Buffer>> delete(String id, Map<String, String> headers) {
    return restClient.delete(BASE_PATH + "/" + id, headers);
  }
}
