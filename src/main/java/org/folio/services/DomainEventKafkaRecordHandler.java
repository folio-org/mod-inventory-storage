package org.folio.services;

import static io.vertx.core.http.HttpMethod.GET;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.domainevent.DomainEvent;

public class DomainEventKafkaRecordHandler implements AsyncRecordHandler<String, String> {

  private static final Logger LOG = LogManager.getLogger(DomainEventKafkaRecordHandler.class);
  private static final String SHARING_JOBS_PATH =
    "/consortia/%s/sharing/instances?status=COMPLETE&instanceIdentifier=%s"; //NOSONAR
  private static final String TENANT_IDS_LIMIT = "100";
  private static final String CONSORTIUM_SOURCE_TEMPLATE = "CONSORTIUM-%s";
  private static final String SHARING_INSTANCES_FIELD = "sharingInstances";
  private static final String TARGET_TENANT_ID_FIELD = "targetTenantId";
  private static final String SOURCE_TENANT_ID_FIELD = "sourceTenantId";

  private final ConsortiumDataCache consortiaDataCache;
  private final Vertx vertx;
  private final HttpClient httpClient;

  public DomainEventKafkaRecordHandler(ConsortiumDataCache consortiaDataCache,
                                       HttpClient httpClient, Vertx vertx) {
    this.consortiaDataCache = consortiaDataCache;
    this.vertx = vertx;
    this.httpClient = httpClient;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      DomainEvent<Instance> event = Json.decodeValue(kafkaRecord.value(), DomainEvent.class);
      LOG.info("handle:: Processing event for shadow instances synchronization, tenantId: '{}'", event.getTenant());
      Map<String, String> headers = new CaseInsensitiveMap<>(KafkaHeaderUtils.kafkaHeadersToMap(kafkaRecord.headers()));
      String instanceId = kafkaRecord.key();
      String tenantId = headers.get(TENANT.toLowerCase());

      if (event.getType() != UPDATE) {
        return Future.succeededFuture(kafkaRecord.key());
      }

      Future<Optional<ConsortiumData>> consortiumDataFuture = consortiaDataCache.getConsortiumData(tenantId, headers);
      return consortiumDataFuture
        .map(consortiumDataOptional -> consortiumDataOptional
          .map(consortiumData -> isCentralTenantId(event.getTenant(), consortiumData))
          .orElse(false))
        .compose(isCentralTenant -> Boolean.TRUE.equals(isCentralTenant)
          ? synchronizeShadowInstances(event, instanceId, consortiumDataFuture.result().get(), headers)
            .map(kafkaRecord.key())
          : Future.succeededFuture(kafkaRecord.key()))
        .onFailure(e -> LOG.warn(
          "handle:: Error during handling event for shadow instances synchronization, centralTenantId: '{}', instanceId: '{}'",
          event.getTenant(), instanceId, e));
    } catch (Exception e) {
      LOG.warn("handle:: Error while handling event for shadow instances synchronization", e);
      return Future.failedFuture(e);
    }
  }

  private boolean isCentralTenantId(String tenantId, ConsortiumData consortiumData) {
    return tenantId.equals(consortiumData.getCentralTenantId());
  }

  private Future<Void> synchronizeShadowInstances(DomainEvent<Instance> event, String instanceId,
                                                  ConsortiumData consortiumData, Map<String, String> headers) {
    LOG.info(
      "synchronizeShadowInstances:: Initializing shadow instances synchronization, instanceId: '{}', centralTenantId: '{}'",
      instanceId, event.getTenant());
    return getInstanceAffiliationTenantIds(consortiumData.getConsortiumId(), consortiumData.getCentralTenantId(),
      instanceId, headers)
      .compose(tenantIds -> updateShadowInstances(event, tenantIds, headers));
  }

  private Future<List<String>> getInstanceAffiliationTenantIds(String consortiumId, String centralTenantId,
                                                               String instanceId, Map<String, String> headers) {
    String okapiUrl = headers.get(URL);
    String preparedPath = String.format(SHARING_JOBS_PATH, consortiumId, instanceId);
    WebClient client = WebClient.wrap(httpClient);
    HttpRequest<Buffer> request = client.requestAbs(GET, okapiUrl + preparedPath);
    headers.forEach(request::putHeader);
    request.addQueryParam("limit", TENANT_IDS_LIMIT);

    return request.send().compose(response -> {
      if (response.statusCode() != 200) {
        String msg =
          String.format("Error retrieving affiliations tenant ids, response status: '%s', response body: '%s'",
            response.statusCode(), response.bodyAsString());
        LOG.warn("getInstanceAffiliationTenantIds:: {}", msg);
        return Future.failedFuture(msg);
      }

      List<String> affiliationsTenantIds = response.bodyAsJsonObject().getJsonArray(SHARING_INSTANCES_FIELD)
        .stream()
        .map(JsonObject.class::cast)
        .flatMap(sharing ->
          Stream.of(sharing.getString(TARGET_TENANT_ID_FIELD), sharing.getString(SOURCE_TENANT_ID_FIELD)))
        .filter(tenantId -> !tenantId.equals(centralTenantId))
        .collect(Collectors.toList());
      return Future.succeededFuture(affiliationsTenantIds);
    });
  }

  private Future<Void> updateShadowInstances(DomainEvent<Instance> event, List<String> tenantIds,
                                             Map<String, String> headers) {
    LOG.info("updateShadowInstances:: trying to update shadow instances in the following tenants: {} ", tenantIds);
    HashMap<String, String> okapiHeaders = new HashMap<>(headers);
    ArrayList<Future<Void>> updateFutures = new ArrayList<>();
    Instance instance = JsonObject.mapFrom(event.getNewEntity()).mapTo(Instance.class);
    prepareInstanceForUpdate(instance);

    for (String tenantId : tenantIds) {
      updateFutures.add(updateShadowInstance(instance, tenantId, okapiHeaders));
    }
    return GenericCompositeFuture.join(updateFutures).mapEmpty();
  }

  private void prepareInstanceForUpdate(Instance instance) {
    instance.setSource(String.format(CONSORTIUM_SOURCE_TEMPLATE, instance.getSource()));
  }

  private Future<Void> updateShadowInstance(Instance instance, String tenantId, HashMap<String, String> okapiHeaders) {
    LOG.info("updateShadowInstance:: Trying to update shadow instance, tenantId: '{}', instanceId: '{}'", tenantId, instance.getId());
    okapiHeaders.put(TENANT, tenantId);
    InstanceRepository instanceRepository = new InstanceRepository(vertx.getOrCreateContext(), okapiHeaders);
    return instanceRepository.getById(instance.getId())
      .map(Instance::getVersion)
      .compose(currentVersion ->  instanceRepository.update(instance.getId(), instance.withVersion(currentVersion)))
      .onFailure(e -> LOG.warn(
        "updateShadowInstance:: Error during shadow instance update, tenantId: '{}', instanceId: '{}'",
        tenantId, instance.getId(), e))
      .onSuccess(v -> LOG.info(
        "updateShadowInstance:: Shadow instance has been updated, tenantId: '{}', instanceId: '{}'",
        tenantId, instance.getId()))
      .mapEmpty();
  }

}
