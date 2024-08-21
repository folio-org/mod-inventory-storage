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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.domainevent.DomainEvent;
import org.folio.utils.ConsortiumUtils;

public class InstanceSynchronizationEventProcessor implements SynchronizationEventProcessor<Instance> {

  private static final Logger LOG = LogManager.getLogger(InstanceSynchronizationEventProcessor.class);
  private static final String EVENT_HANDLING_ERROR_MSG =
    "handle:: Failed to handle event for shadow instances synchronization, centralTenantId: '{}', instanceId: '{}'";
  private static final String SHARING_INSTANCES_PATH =
    "/consortia/%s/sharing/instances?status=COMPLETE&instanceIdentifier=%s"; //NOSONAR
  private static final String INSTANCES_PARALLEL_UPDATES_COUNT_PARAM =
    "instance-synchronization.parallel.updates.count";
  private static final int DEFAULT_INSTANCES_PARALLEL_UPDATES_COUNT = 10;
  private static final String LIMIT_QUERY_PARAM = "limit";
  private static final String TENANT_IDS_LIMIT = "1000";
  private static final String CONSORTIUM_SOURCE_TEMPLATE = "CONSORTIUM-%s";
  private static final String SHARING_INSTANCES_FIELD = "sharingInstances";
  private static final String TARGET_TENANT_ID_FIELD = "targetTenantId";
  private static final String SOURCE_TENANT_ID_FIELD = "sourceTenantId";

  private final int instancesParallelUpdatesLimit;

  public InstanceSynchronizationEventProcessor() {
    this.instancesParallelUpdatesLimit = Config.getSysConfInteger(INSTANCES_PARALLEL_UPDATES_COUNT_PARAM,
      DEFAULT_INSTANCES_PARALLEL_UPDATES_COUNT, new JsonObject());
  }

  @Override
  public Future<String> process(DomainEvent<?> event, String instanceId, SynchronizationContext context) {
    LOG.debug("process:: Processing event, tenantId: '{}'", event.getTenant());
    try {

      if (event.getType() != UPDATE) {
        return Future.succeededFuture(instanceId);
      }

      var isCentralTenant = ConsortiumUtils.isCentralTenant(event.getTenant(), context.consortiaData());
      if (isCentralTenant) {
        return synchronizeShadowInstances(event, instanceId, context)
          .map(instanceId)
          .onFailure(e -> LOG.warn(EVENT_HANDLING_ERROR_MSG, event.getTenant(), instanceId, e));
      } else {
        return Future.succeededFuture(instanceId);
      }
    } catch (Exception e) {
      LOG.warn("process:: Error while handling event for shadow instances synchronization", e);
      return Future.failedFuture(e);
    }
  }

  private Future<Void> synchronizeShadowInstances(DomainEvent<?> event, String instanceId,
                                                  SynchronizationContext context) {
    return getShadowInstancesTenantIds(instanceId, context)
      .compose(tenantIds -> updateShadowInstances(event, tenantIds, context));
  }

  private Future<List<String>> getShadowInstancesTenantIds(String instanceId, SynchronizationContext context) {
    var consortiumData = context.consortiaData();
    var consortiumId = consortiumData.consortiumId();
    var centralTenantId = consortiumData.centralTenantId();
    var headers = context.headers();
    String okapiUrl = headers.get(URL);
    String preparedPath = format(SHARING_INSTANCES_PATH, consortiumId, instanceId);
    WebClient client = WebClient.wrap(context.httpClient());
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

  private Future<Void> updateShadowInstances(DomainEvent<?> event, List<String> tenantIds,
                                             SynchronizationContext context) {
    try {
      LOG.info("updateShadowInstances:: Trying to update shadow instances in the following tenants: {} ", tenantIds);
      Instance instance = PostgresClient.pojo2JsonObject(event.getNewEntity()).mapTo(Instance.class);
      prepareInstanceForUpdate(instance);
      List<List<String>> tenantsChunks = Lists.partition(tenantIds, instancesParallelUpdatesLimit);

      Future<CompositeFuture> future = Future.succeededFuture();
      for (List<String> tenantsChunk : tenantsChunks) {
        future = future.eventually(() -> updateShadowInstances(tenantsChunk, instance, context));
      }
      return future.mapEmpty();
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private CompositeFuture updateShadowInstances(List<String> tenantIds, Instance instance,
                                                SynchronizationContext context) {
    ArrayList<Future<Void>> updateFutures = new ArrayList<>();
    for (String tenantId : tenantIds) {
      updateFutures.add(updateShadowInstance(instance, tenantId, context));
    }
    return GenericCompositeFuture.join(updateFutures);
  }

  private void prepareInstanceForUpdate(Instance instance) {
    instance.setSource(format(CONSORTIUM_SOURCE_TEMPLATE, instance.getSource()));
  }

  private Future<Void> updateShadowInstance(Instance instance, String targetTenant, SynchronizationContext context) {
    LOG.debug("updateShadowInstance:: Trying to update shadow instance, targetTenant: '{}', instanceId: '{}'",
      targetTenant, instance.getId());
    var headers = context.headers();
    headers.put(TENANT, targetTenant);
    InstanceRepository instanceRepository = new InstanceRepository(context.vertx().getOrCreateContext(), headers);

    return instanceRepository.getById(instance.getId())
      .map(Instance::getVersion)
      .compose(currentVersion -> instanceRepository.update(instance.getId(), instance.withVersion(currentVersion)))
      .onFailure(e -> LOG.warn(
        "updateShadowInstance:: Error during shadow instance update, targetTenant: '{}', instanceId: '{}'",
        targetTenant, instance.getId(), e))
      .onSuccess(v -> LOG.info(
        "updateShadowInstance:: Shadow instance has been updated, targetTenant: '{}', instanceId: '{}'",
        targetTenant, instance.getId()))
      .mapEmpty();
  }
}
