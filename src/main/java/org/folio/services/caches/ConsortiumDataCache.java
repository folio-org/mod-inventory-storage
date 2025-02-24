package org.folio.services.caches;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsortiumDataCache {

  private static final Logger LOG = LogManager.getLogger(ConsortiumDataCache.class);
  private static final String EXPIRATION_TIME_PARAM = "cache.consortium-data.expiration.time.seconds";
  private static final String DEFAULT_EXPIRATION_TIME_SECONDS = "300";
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";
  private static final String CONSORTIUM_TENANTS_PATH = "/consortia/%s/tenants";
  private static final String USER_TENANTS_FIELD = "userTenants";
  private static final String CONSORTIUM_TENANTS_FIELD = "tenants";
  private static final String CENTRAL_TENANT_ID_FIELD = "centralTenantId";
  private static final String CONSORTIUM_ID_FIELD = "consortiumId";

  private final HttpClient httpClient;
  private final AsyncCache<String, Optional<ConsortiumData>> cache;

  public ConsortiumDataCache(Vertx vertx, HttpClient httpClient) {
    int expirationTime = Integer.parseInt(System.getProperty(EXPIRATION_TIME_PARAM, DEFAULT_EXPIRATION_TIME_SECONDS));
    this.httpClient = httpClient;
    this.cache = Caffeine.newBuilder()
      .expireAfterWrite(expirationTime, TimeUnit.SECONDS)
      .executor(task -> vertx.runOnContext(v -> task.run()))
      .buildAsync();
  }

  /**
   * Returns consortium data by specified {@code tenantId}.
   *
   * @param headers - okapi headers
   * @return future of Optional with consortium data for the specified {@code tenantId},
   *   if the specified {@code tenantId} is not included to any consortium, then returns future with empty Optional
   */
  public Future<Optional<ConsortiumData>> getConsortiumData(Map<String, String> headers) {
    Map<String, String> caseInsensitiveHeaders = new CaseInsensitiveMap<>(headers);
    String tenantId = caseInsensitiveHeaders.get(TENANT);
    return getConsortiumData(tenantId, headers);
  }

  /**
   * Returns consortium data by specified {@code tenantId}.
   *
   * @param tenantId - tenant id
   * @param headers  - okapi headers
   * @return future of Optional with consortium data for the specified {@code tenantId},
   *   if the specified {@code tenantId} is not included to any consortium, then returns future with empty Optional
   */
  public Future<Optional<ConsortiumData>> getConsortiumData(String tenantId, Map<String, String> headers) {
    try {
      return Future.fromCompletionStage(cache.get(tenantId, (tenant, executor) -> loadConsortiumData(tenant, headers)));
    } catch (Exception e) {
      LOG.warn("getConsortiumData:: Error loading consortium data, tenantId: '{}'", tenantId, e);
      return failedFuture(e);
    }
  }

  private CompletableFuture<Optional<ConsortiumData>> loadConsortiumData(String tenantId, Map<String, String> headers) {
    var request = getHttpRequest(headers, USER_TENANTS_PATH);

    return getResponse(request)
      .compose(responseBody -> {
        if (responseBody.isEmpty()) {
          return succeededFuture(Optional.<ConsortiumData>empty());
        }
        JsonArray userTenants = responseBody.get().getJsonArray(USER_TENANTS_FIELD);
        if (userTenants.isEmpty()) {
          return succeededFuture(Optional.<ConsortiumData>empty());
        }

        LOG.info("loadConsortiumData:: Consortium data was loaded, tenantId: '{}'", tenantId);
        JsonObject userTenant = userTenants.getJsonObject(0);
        var centralTenantId = userTenant.getString(CENTRAL_TENANT_ID_FIELD);
        var consortiumId = userTenant.getString(CONSORTIUM_ID_FIELD);
        return loadConsortiumTenants(consortiumId, headers)
          .map(memberTenants -> Optional.of(new ConsortiumData(centralTenantId, consortiumId, memberTenants)));
      })
      .toCompletionStage()
      .toCompletableFuture();
  }

  private Future<List<String>> loadConsortiumTenants(String consortiumId,
                                                     Map<String, String> headers) {
    var request = getHttpRequest(headers, CONSORTIUM_TENANTS_PATH.formatted(consortiumId));
    return getResponse(request)
      .map(responseBody -> responseBody.map(entries -> entries.getJsonArray(CONSORTIUM_TENANTS_FIELD)
        .stream()
        .map(o -> ((JsonObject) o).mapTo(ConsortiumTenant.class))
        .filter(consortiumTenant -> !consortiumTenant.isCentral())
        .map(ConsortiumTenant::id)
        .toList()).orElse(Collections.emptyList())
      )
      .recover(throwable -> succeededFuture(Collections.emptyList()));
  }

  private HttpRequest<Buffer> getHttpRequest(Map<String, String> headers, String path) {
    String okapiUrl = headers.get(URL);
    if (okapiUrl == null) {
      LOG.error("getHttpRequest:: Okapi URL is not specified in headers");
      throw new IllegalArgumentException("Okapi URL is not specified in headers");
    }
    WebClient client = WebClient.wrap(httpClient);
    HttpRequest<Buffer> request = client.requestAbs(GET, okapiUrl + path);
    headers.forEach(request::putHeader);
    return request;
  }

  private Future<Optional<JsonObject>> getResponse(HttpRequest<Buffer> request) {
    var methodName = request.method().name();
    var requestUri = request.uri();
    LOG.info("getResponse:: Try to request method='{}' uri='{}'", methodName, requestUri);
    return request.send().compose(response -> {
      if (response.statusCode() != HTTP_OK) {
        String msg = String.format("Failed to request method='%s' uri='%s', status='%s', body='%s'",
          methodName, requestUri, response.statusCode(), response.bodyAsString());
        LOG.warn("getResponse:: {}", msg);
        return failedFuture(msg);
      }
      var responseBody = response.bodyAsJsonObject();
      return succeededFuture(Optional.of(responseBody));
    });
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConsortiumTenant(String id, boolean isCentral) { }
}
