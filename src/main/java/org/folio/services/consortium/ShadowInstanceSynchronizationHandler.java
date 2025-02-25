package org.folio.services.consortium;

import static io.vertx.core.http.HttpMethod.GET;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import com.google.common.collect.Lists;
import io.vertx.core.CompositeFuture;
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
import java.util.stream.Stream;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaHeaderUtils;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.domainevent.DomainEvent;

public class ShadowInstanceSynchronizationHandler implements AsyncRecordHandler<String, String> {

  private static final Logger LOG = LogManager.getLogger(ShadowInstanceSynchronizationHandler.class);
  private static final String EVENT_HANDLING_ERROR_MSG =
    "handle:: Failed to handle event for shadow instances synchronization, centralTenantId: '{}', instanceId: '{}'";
  private static final String SHARING_INSTANCES_PATH =
    "/consortia/%s/sharing/instances?status=COMPLETE&instanceIdentifier=%s";
  private static final String INSTANCES_PARALLEL_UPDATES_COUNT_PARAM =
    "instance-synchronization.parallel.updates.count";
  private static final String DEFAULT_INSTANCES_PARALLEL_UPDATES_COUNT = "10";
  private static final String LIMIT_QUERY_PARAM = "limit";
  private static final String TENANT_IDS_LIMIT = "1000";
  private static final String CONSORTIUM_SOURCE_TEMPLATE = "CONSORTIUM-%s";
  private static final String SHARING_INSTANCES_FIELD = "sharingInstances";
  private static final String TARGET_TENANT_ID_FIELD = "targetTenantId";
  private static final String SOURCE_TENANT_ID_FIELD = "sourceTenantId";


  private final ConsortiumDataCache consortiaDataCache;
  private final Vertx vertx;
  private final HttpClient httpClient;
  private final int instancesParallelUpdatesLimit;

  public ShadowInstanceSynchronizationHandler(ConsortiumDataCache consortiaDataCache,
                                              HttpClient httpClient, Vertx vertx) {
    this.instancesParallelUpdatesLimit = Integer.parseInt(
      System.getProperty(INSTANCES_PARALLEL_UPDATES_COUNT_PARAM, DEFAULT_INSTANCES_PARALLEL_UPDATES_COUNT));
    this.consortiaDataCache = consortiaDataCache;
    this.vertx = vertx;
    this.httpClient = httpClient;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      DomainEvent<Instance> event = Json.decodeValue(kafkaRecord.value(), DomainEvent.class);
      LOG.debug("handle:: Processing event, tenantId: '{}'", event.getTenant());
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
        .onFailure(e -> LOG.warn(EVENT_HANDLING_ERROR_MSG, event.getTenant(), instanceId, e));
    } catch (Exception e) {
      LOG.warn("handle:: Error while handling event for shadow instances synchronization", e);
      return Future.failedFuture(e);
    }
  }

  private boolean isCentralTenantId(String tenantId, ConsortiumData consortiumData) {
    return tenantId.equals(consortiumData.centralTenantId());
  }

  private Future<Void> synchronizeShadowInstances(DomainEvent<Instance> event, String instanceId,
                                                  ConsortiumData consortiumData, Map<String, String> headers) {
    return getShadowInstancesTenantIds(consortiumData.consortiumId(), consortiumData.centralTenantId(),
      instanceId, headers)
      .compose(tenantIds -> updateShadowInstances(event, tenantIds, headers));
  }

  private Future<List<String>> getShadowInstancesTenantIds(String consortiumId, String centralTenantId,
                                                           String instanceId, Map<String, String> headers) {
    String okapiUrl = headers.get(URL);
    String preparedPath = format(SHARING_INSTANCES_PATH, consortiumId, instanceId);
    WebClient client = WebClient.wrap(httpClient);
    HttpRequest<Buffer> request = client.requestAbs(GET, okapiUrl + preparedPath);
    headers.forEach(request::putHeader);
    request.addQueryParam(LIMIT_QUERY_PARAM, TENANT_IDS_LIMIT);

    return request.send().compose(response -> {
      if (response.statusCode() != HTTP_OK) {
        String msg = format("Error retrieving shadow instances tenant ids, response status: '%s', response body: '%s'",
          response.statusCode(), response.bodyAsString());
        LOG.warn("getShadowInstancesTenantIds:: {}", msg);
        return Future.failedFuture(msg);
      }

      List<String> affiliationsTenantIds = response.bodyAsJsonObject().getJsonArray(SHARING_INSTANCES_FIELD)
        .stream()
        .map(JsonObject.class::cast)
        .flatMap(sharing ->
          Stream.of(sharing.getString(TARGET_TENANT_ID_FIELD), sharing.getString(SOURCE_TENANT_ID_FIELD)))
        .filter(tenantId -> !tenantId.equals(centralTenantId))
        .toList();
      return Future.succeededFuture(affiliationsTenantIds);
    });
  }

  private Future<Void> updateShadowInstances(DomainEvent<Instance> event, List<String> tenantIds,
                                             Map<String, String> headers) {
    try {
      LOG.info("updateShadowInstances:: Trying to update shadow instances in the following tenants: {} ", tenantIds);
      Instance instance = PostgresClient.pojo2JsonObject(event.getNewEntity()).mapTo(Instance.class);
      prepareInstanceForUpdate(instance);
      List<List<String>> tenantsChunks = Lists.partition(tenantIds, instancesParallelUpdatesLimit);

      Future<CompositeFuture> future = Future.succeededFuture();
      for (List<String> tenantsChunk : tenantsChunks) {
        future = future.eventually(() -> updateShadowInstances(tenantsChunk, instance, headers));
      }
      return future.mapEmpty();
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private CompositeFuture updateShadowInstances(List<String> tenantIds, Instance instance,
                                                Map<String, String> headers) {
    ArrayList<Future<Void>> updateFutures = new ArrayList<>();
    for (String tenantId : tenantIds) {
      updateFutures.add(updateShadowInstance(instance, tenantId, headers));
    }
    return GenericCompositeFuture.join(updateFutures);
  }

  private void prepareInstanceForUpdate(Instance instance) {
    instance.setSource(format(CONSORTIUM_SOURCE_TEMPLATE, instance.getSource()));
  }

  private Future<Void> updateShadowInstance(Instance instance, String tenantId, Map<String, String> okapiHeaders) {
    LOG.debug("updateShadowInstance:: Trying to update shadow instance, tenantId: '{}', instanceId: '{}'",
      tenantId, instance.getId());
    HashMap<String, String> headers = new HashMap<>(okapiHeaders);
    headers.put(TENANT, tenantId);
    InstanceRepository instanceRepository = new InstanceRepository(vertx.getOrCreateContext(), headers);

    return instanceRepository.getById(instance.getId())
      .map(Instance::getVersion)
      .compose(currentVersion -> instanceRepository.update(instance.getId(), instance.withVersion(currentVersion)))
      .onFailure(e -> LOG.warn(
        "updateShadowInstance:: Error during shadow instance update, tenantId: '{}', instanceId: '{}'",
        tenantId, instance.getId(), e))
      .onSuccess(v -> LOG.info(
        "updateShadowInstance:: Shadow instance has been updated, tenantId: '{}', instanceId: '{}'",
        tenantId, instance.getId()))
      .mapEmpty();
  }

}
