package org.folio.services.consortium;

import static io.vertx.core.http.HttpMethod.POST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.consortium.entities.SharingStatus;
import org.folio.services.consortium.exceptions.ConsortiumException;

public class ConsortiumServiceImpl implements ConsortiumService {
  private static final Logger LOGGER = LogManager.getLogger(ConsortiumServiceImpl.class);
  private static final String SHARE_INSTANCE_ENDPOINT = "/consortia/%s/sharing/instances";
  private static final String SHARING_INSTANCE_ERROR = "Error during sharing Instance for sourceTenantId:"
    + " %s, targetTenantId: %s, instanceIdentifier: %s, status code: %s, response message: %s";
  private final HttpClient httpClient;
  private final ConsortiumDataCache consortiumDataCache;

  public ConsortiumServiceImpl(HttpClient httpClient, ConsortiumDataCache consortiumDataCache) {
    this.httpClient = httpClient;
    this.consortiumDataCache = consortiumDataCache;
  }

  @Override
  public Future<SharingInstance> createShadowInstance(String instanceId, ConsortiumData consortiumData,
                                                      Map<String, String> headers) {
    LOGGER.info("createShadowInstance:: instance with id: {} is not found in local tenant."
          + " Trying to create a shadow instance", instanceId);
  SharingInstance sharingInstance = new SharingInstance();
    String centralTenantId = consortiumData.centralTenantId();
    sharingInstance.setSourceTenantId(centralTenantId);
    sharingInstance.setInstanceIdentifier(UUID.fromString(instanceId));
    sharingInstance.setTargetTenantId(headers.get(TENANT));

    Map<String, String> requestHeaders = new CaseInsensitiveMap<>(headers);
    requestHeaders.put(TENANT, centralTenantId);

    return shareInstance(consortiumData.consortiumId(), sharingInstance, requestHeaders);
  }

  @Override
  public Future<Optional<ConsortiumData>> getConsortiumData(Map<String, String> headers) {
    return consortiumDataCache.getConsortiumData(headers.get(TENANT), headers);
  }

  // Returns successful future if the sharing status is "IN_PROGRESS" or "COMPLETE"
  @Override
  public Future<SharingInstance> shareInstance(String consortiumId, SharingInstance sharingInstance,
                                               Map<String, String> headers) {
    return buildHttpRequest(String.format(SHARE_INSTANCE_ENDPOINT, consortiumId), POST, headers)
      .sendJson(sharingInstance)
      .compose(httpResponse -> {
        if (httpResponse.statusCode() == HTTP_CREATED
          && !SharingStatus.ERROR.toString().equals(httpResponse.bodyAsJsonObject().getString("status"))) {
          SharingInstance response = Json.decodeValue(httpResponse.body(), SharingInstance.class);
          LOGGER.debug("shareInstance:: Successfully sharedInstance with id: {}, sharedInstance: {}",
            response.getInstanceIdentifier(), httpResponse.bodyAsString());
          return Future.succeededFuture(response);
        } else {
          String message = String.format(SHARING_INSTANCE_ERROR, sharingInstance.getSourceTenantId(),
            sharingInstance.getTargetTenantId(), sharingInstance.getInstanceIdentifier(),
            httpResponse.statusCode(), httpResponse.bodyAsString());
          LOGGER.warn(String.format("shareInstance:: %s", message));
          return Future.failedFuture(new ConsortiumException(message));
        }
      });
  }

  private HttpRequest<Buffer> buildHttpRequest(String path, HttpMethod httpMethod, Map<String, String> headers) {
    String okapiUrl = headers.get(URL);
    WebClient client = WebClient.wrap(httpClient);
    HttpRequest<Buffer> request = client.requestAbs(httpMethod, okapiUrl + path);
    headers.forEach(request::putHeader);

    return request;
  }
}
