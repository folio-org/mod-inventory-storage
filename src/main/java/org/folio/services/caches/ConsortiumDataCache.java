package org.folio.services.caches;

import static io.vertx.core.http.HttpMethod.GET;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsortiumDataCache {

  private static final Logger LOG = LogManager.getLogger(ConsortiumDataCache.class);
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1"; //NOSONAR
  private static final String CONSORTIA_KEY = "consortia";
  private static final String USER_TENANTS_FIELD = "userTenants";
  private static final String CENTRAL_TENANT_ID_FIELD = "centralTenantId";
  private static final String CONSORTIUM_ID_FIELD = "consortiumId";

  private final HttpClient httpClient;
  private final AsyncCache<String, Optional<ConsortiumData>> cache;

  public ConsortiumDataCache(Vertx vertx, HttpClient httpClient) {
    this.httpClient = httpClient;
    this.cache = Caffeine.newBuilder()
      .executor(task -> vertx.runOnContext(v -> task.run()))
      .buildAsync();
  }

  public Future<Optional<ConsortiumData>> getConsortiumData(Map<String, String> headers) {
    try {
      return Future.fromCompletionStage(cache.get(CONSORTIA_KEY, (key, executor) -> loadConsortiumData(headers)));
    } catch (Exception e) {
      LOG.warn("getConsortiumData:: Error loading consortium data", e);
      return Future.failedFuture(e);
    }
  }

  private CompletableFuture<Optional<ConsortiumData>> loadConsortiumData(Map<String, String> headers) {
    String okapiUrl = headers.get(URL);
    WebClient client = WebClient.wrap(httpClient);
    HttpRequest<Buffer> request = client.requestAbs(GET, okapiUrl + USER_TENANTS_PATH);
    headers.forEach(request::putHeader);

    return request.send().compose(response -> {
      if (response.statusCode() != HTTP_OK) {
        String msg = String.format("Error loading consortium data, response status: %s, body: '%s'",
          response.statusCode(), response.bodyAsString());
        LOG.warn("loadConsortiumData:: {}", msg);
        return Future.failedFuture(msg);
      }
      JsonArray userTenants = response.bodyAsJsonObject().getJsonArray(USER_TENANTS_FIELD);
      if (userTenants.isEmpty()) {
        return Future.succeededFuture(Optional.<ConsortiumData>empty());
      }

      LOG.info("loadConsortiumData:: Consortium data was loaded");
      JsonObject userTenant = userTenants.getJsonObject(0);
      return Future.succeededFuture(Optional.of(
        new ConsortiumData(userTenant.getString(CENTRAL_TENANT_ID_FIELD), userTenant.getString(CONSORTIUM_ID_FIELD))));
    }).toCompletionStage()
      .toCompletableFuture();
  }

}
