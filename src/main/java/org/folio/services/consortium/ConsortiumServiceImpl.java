package org.folio.services.consortium;

import static org.folio.rest.support.ContextUtil.constructContext;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClient;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.support.Context;
import org.folio.rest.support.http.client.OkapiHttpClient;
import org.folio.services.consortium.entities.ConsortiumConfiguration;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.consortium.entities.SharingStatus;
import org.folio.services.consortium.exceptions.ConsortiumException;

public class ConsortiumServiceImpl implements ConsortiumService {
  private static final Logger LOGGER = LogManager.getLogger(ConsortiumServiceImpl.class);
  private static final String USER_TENANTS_ENDPOINT = "/user-tenants?limit=1";
  private static final String SHARE_INSTANCE_ENDPOINT = "/consortia/%s/sharing/instances";
  private static final String SHARING_INSTANCE_ERROR = "Error during sharing Instance for sourceTenantId:"
    + " %s, targetTenantId: %s, instanceIdentifier: %s, status code: %s, response message: %s";
  private final HttpClient httpClient;

  public ConsortiumServiceImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<SharingInstance> createShadowInstance(Context context, String instanceId,
                                                      ConsortiumConfiguration consortiumConfiguration) {
    SharingInstance sharingInstance = new SharingInstance();
    sharingInstance.setSourceTenantId(consortiumConfiguration.getCentralTenantId());
    sharingInstance.setInstanceIdentifier(UUID.fromString(instanceId));
    sharingInstance.setTargetTenantId(context.getTenantId());

    Context centralTenantContext = constructContext(consortiumConfiguration.getCentralTenantId(),
      context.getToken(), context.getOkapiLocation());
    return shareInstance(centralTenantContext, consortiumConfiguration.getConsortiumId(), sharingInstance);
  }

  @Override
  public Future<Optional<ConsortiumConfiguration>> getConsortiumConfiguration(Context context) {
    CompletableFuture<Optional<ConsortiumConfiguration>> completableFuture = createOkapiHttpClient(context)
      .thenCompose(client ->
        client.get(context.getOkapiLocation() + USER_TENANTS_ENDPOINT).toCompletableFuture()
          .thenCompose(httpResponse -> {
            if (httpResponse.getStatusCode() == HttpStatus.SC_OK) {
              JsonArray userTenants = httpResponse.getJson().getJsonArray("userTenants");
              if (userTenants.isEmpty()) {
                LOGGER.debug("getCentralTenantId:: Central tenant and consortium id not found");
                return CompletableFuture.completedFuture(Optional.empty());
              }
              String centralTenantId = userTenants.getJsonObject(0).getString("centralTenantId");
              String consortiumId = userTenants.getJsonObject(0).getString("consortiumId");
              LOGGER.debug("getCentralTenantId:: Found centralTenantId: {} and consortiumId: {}",
                centralTenantId, consortiumId);
              return CompletableFuture.completedFuture(Optional.of(
                new ConsortiumConfiguration(centralTenantId, consortiumId)));
            } else {
              String message = String.format("Error retrieving centralTenantId by tenant id:"
                  + " %s, status code: %s, response message: %s", context.getTenantId(),
                httpResponse.getStatusCode(), httpResponse.getBody());
              LOGGER.warn(String.format("getCentralTenantId:: %s", message));
              return CompletableFuture.failedFuture(new ConsortiumException(message));
            }
          }));
    return Future.fromCompletionStage(completableFuture);
  }

  // Returns successful future if the sharing status is "IN_PROGRESS" or "COMPLETE"
  @Override
  public Future<SharingInstance> shareInstance(Context context, String consortiumId, SharingInstance sharingInstance) {
    CompletableFuture<SharingInstance> completableFuture = createOkapiHttpClient(context)
      .thenCompose(client ->
        client.post(context.getOkapiLocation() + String.format(SHARE_INSTANCE_ENDPOINT, consortiumId),
            Json.encode(sharingInstance))
          .thenCompose(httpResponse -> {
            if (httpResponse.getStatusCode() == HttpStatus.SC_CREATED
              && !SharingStatus.ERROR.toString().equals(httpResponse.getJson().getString("status"))) {
              SharingInstance response = Json.decodeValue(httpResponse.getBody(), SharingInstance.class);
              LOGGER.debug("shareInstance:: Successfully sharedInstance with id: {}, sharedInstance: {}",
                response.getInstanceIdentifier(), httpResponse.getBody());
              return CompletableFuture.completedFuture(response);
            } else {
              String message = String.format(SHARING_INSTANCE_ERROR, sharingInstance.getSourceTenantId(),
                sharingInstance.getTargetTenantId(), sharingInstance.getInstanceIdentifier(),
                httpResponse.getStatusCode(), httpResponse.getBody());
              LOGGER.warn(String.format("shareInstance:: %s", message));
              return CompletableFuture.failedFuture(new ConsortiumException(message));
            }
          }));
    return Future.fromCompletionStage(completableFuture);
  }

  private CompletableFuture<OkapiHttpClient> createOkapiHttpClient(Context context) {
    try {
      return CompletableFuture.completedFuture(new OkapiHttpClient(WebClient.wrap(httpClient),
        new URL(context.getOkapiLocation()), context.getTenantId(), context.getToken(), null, null, null));
    } catch (MalformedURLException e) {
      LOGGER.warn("createOkapiHttpClient:: Error during creation of OkapiHttpClient for URL: {}",
        context.getOkapiLocation(), e);
      return CompletableFuture.failedFuture(e);
    }
  }
}
