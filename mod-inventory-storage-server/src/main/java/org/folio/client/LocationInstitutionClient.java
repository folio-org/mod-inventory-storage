package org.folio.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import org.folio.rest.jaxrs.model.LocationInstitution;

public class LocationInstitutionClient implements StorageModuleClient<LocationInstitution> {

  private static final String BASE_PATH = "/location-storage/institutions";

  private final RestClient restClient;

  public LocationInstitutionClient(RestClient restClient) {
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
  public Future<HttpResponse<Buffer>> create(LocationInstitution item, Map<String, String> headers) {
    return restClient.post(BASE_PATH, headers, item);
  }

  @Override
  public Future<HttpResponse<Buffer>> update(String id, LocationInstitution item, Map<String, String> headers) {
    return restClient.put(BASE_PATH + "/" + id, headers, item);
  }

  @Override
  public Future<HttpResponse<Buffer>> delete(String id, Map<String, String> headers) {
    return restClient.delete(BASE_PATH + "/" + id, headers);
  }

  public Future<HttpResponse<Buffer>> getByQuery(String cql, int offset, int limit, boolean includeShadow,
                                                 Map<String, String> headers) {
    return restClient.get(buildQueryPath(cql, offset, limit, includeShadow), headers);
  }

  private String buildQueryPath(String cql, int offset, int limit, boolean includeShadow) {
    var params = new StringJoiner("&", BASE_PATH + "?", "");
    params.add("offset=" + offset);
    params.add("limit=" + limit);
    if (cql != null && !cql.isBlank()) {
      params.add("query=" + URLEncoder.encode(cql, StandardCharsets.UTF_8));
    }
    params.add("includeShadow=" + includeShadow);
    return params.toString();
  }
}
