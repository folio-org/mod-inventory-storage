package org.folio.services.caches;

import static io.vertx.core.http.HttpMethod.GET;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.okapi.common.XOkapiHeaders.URL;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsortiumDataCache {

  private static final Logger LOG = LogManager.getLogger(ConsortiumDataCache.class);
  private static final String EXPIRATION_TIME_PARAM = "cache.consortium-data.expiration.time.seconds";
  private static final String DEFAULT_EXPIRATION_TIME_SECONDS = "300";
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1"; //NOSONAR
  private static final String USER_TENANTS_FIELD = "userTenants";
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
   * @param tenantId - tenant id
   * @param headers  - okapi headers
   * @return future of Optional with consortium data for the specified {@code tenantId},
   *   if the specified {@code tenantId} is not included to any consortium, then returns future with empty Optional
   */
  public Future<Optional<ConsortiumData>> getConsortiumData(String tenantId, Map<String, String> headers) {
    try {
      return Future.fromCompletionStage(cache.get(tenantId, (key, executor) -> loadConsortiumData(key, headers)));
    } catch (Exception e) {
      LOG.warn("getConsortiumData:: Error loading consortium data, tenantId: '{}'", tenantId, e);
      return Future.failedFuture(e);
    }
  }

  private CompletableFuture<Optional<ConsortiumData>> loadConsortiumData(String tenantId, Map<String, String> headers) {
    String okapiUrl = headers.get(URL);
    WebClient client = WebClient.wrap(httpClient);
    HttpRequest<Buffer> request = client.requestAbs(GET, okapiUrl + USER_TENANTS_PATH);
    headers.forEach(request::putHeader);

    return request.send().compose(response -> {
      if (response.statusCode() != HTTP_OK) {
        String msg = String.format("Error loading consortium data, tenantId: '%s' response status: '%s', body: '%s'",
          tenantId, response.statusCode(), response.bodyAsString());
        LOG.warn("loadConsortiumData:: {}", msg);
        if (response.statusCode() == HTTP_FORBIDDEN) {
          return Future.succeededFuture(Optional.<ConsortiumData>empty());
        }

        return Future.failedFuture(msg);
      }
      JsonArray userTenants = response.bodyAsJsonObject().getJsonArray(USER_TENANTS_FIELD);
      if (userTenants.isEmpty()) {
        return Future.succeededFuture(Optional.<ConsortiumData>empty());
      }

      LOG.info("loadConsortiumData:: Consortium data was loaded, tenantId: '{}'", tenantId);
      JsonObject userTenant = userTenants.getJsonObject(0);
      return Future.succeededFuture(Optional.of(
        new ConsortiumData(userTenant.getString(CENTRAL_TENANT_ID_FIELD), userTenant.getString(CONSORTIUM_ID_FIELD))));
    }).toCompletionStage()
      .toCompletableFuture();
  }

}
