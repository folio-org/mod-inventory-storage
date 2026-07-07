package org.folio.rest.impl;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.liquibase.LiquibaseUtil;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.services.migration.BaseMigrationService;
import org.folio.utils.SampleDataIdRandomizer;

public class TenantRefApi extends TenantAPI {

  private static final String SAMPLE_LEAD = "sample-data";
  private static final String SAMPLE_KEY = "loadSample";
  private static final String REFERENCE_KEY = "loadReference";
  private static final String REFERENCE_LEAD = "ref-data";
  private static final String INSTANCES = "instance-storage/instances";
  private static final String HOLDINGS = "holdings-storage/holdings";
  private static final String ITEMS = "item-storage/items";
  private static final String INSTANCE_RELATIONSHIPS = "instance-storage/instance-relationships";
  private static final String BOUND_WITH_PARTS = "inventory-storage/bound-with-parts";

  private static final Logger log = LogManager.getLogger();
  final String[] refPaths = new String[] {
    "alternative-title-types",
    "call-number-types",
    "classification-types",
    "contributor-name-types",
    "contributor-types",
    "electronic-access-relationships",
    "holdings-note-types",
    "holdings-sources",
    "holdings-types",
    "identifier-types",
    "ill-policies",
    "instance-formats",
    "instance-note-types",
    "instance-relationship-types",
    "instance-statuses",
    "instance-types",
    "item-damaged-statuses",
    "item-note-types",
    "loan-types",
    "material-types",
    "modes-of-issuance",
    "nature-of-content-terms",
    "statistical-code-types",
    "statistical-codes",
    "subject-sources",
    "subject-types"
  };

  @Validate
  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
                         Handler<AsyncResult<Response>> handler, Context context) {
    // delete Kafka topics if tenant purged
    var result = tenantAttributes.getPurge() != null && tenantAttributes.getPurge()
                 ? new KafkaAdminClientService(context.owner())
                   .deleteKafkaTopics(InventoryKafkaTopic.values(), tenantId(headers))
                 : Future.succeededFuture();
    result.onComplete(x -> super.postTenant(tenantAttributes, headers, handler, context));
  }

  @Validate
  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
                           Map<String, String> headers, Context vertxContext) {
    var vertx = vertxContext.owner();

    // create topics before loading data
    var future = new KafkaAdminClientService(vertx)
      .createKafkaTopics(InventoryKafkaTopic.values(), tenantId)
      .compose(x -> super.loadData(attributes, tenantId, headers, vertxContext))
      .compose(num -> vertx.executeBlocking(() -> {
        LiquibaseUtil.initializeSchemaForTenant(vertx, tenantId);
        log.info("loadData:: Liquibase schema initialization completed for tenant {}", tenantId);
        return null;
      }).map(v -> num));

    future = loadReferenceData(attributes, headers, vertxContext, future);
    future = loadSampleData(attributes, headers, vertxContext, future);

    return future.compose(result -> runJavaMigrations(attributes, vertxContext, headers)
      .map(result));
  }

  private Future<Integer> loadReferenceData(TenantAttributes attributes, Map<String, String> headers,
                                            Context vertxContext, Future<Integer> future) {
    var tl = new TenantLoading();
    configureReferenceData(tl);
    return future.compose(n -> tl.perform(attributes, headers, vertxContext, n));
  }

  private Future<Integer> loadSampleData(TenantAttributes attributes, Map<String, String> headers,
                                         Context vertxContext, Future<Integer> future) {
    var tl = new TenantLoading();
    configureSampleData(tl);
    return future.compose(n -> tl.perform(attributes, headers, vertxContext, n));
  }

  private void configureReferenceData(TenantLoading tl) {
    tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD);
    tl.withPostIgnore();
    for (String p : refPaths) {
      tl.add(p);
    }
  }

  private void configureSampleData(TenantLoading tl) {
    tl.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD);
    tl.withPostIgnore();
    tl.add("service-points");
    tl.add("service-points-users");
    tl.add("location-units/institutions");
    tl.add("location-units/campuses");
    tl.add("location-units/libraries");
    tl.add("locations");
    tl.add("holdings-sources");

    var idRandomizer = new SampleDataIdRandomizer();

    // Randomize instance IDs and HRIDs
    tl.withFilter(json -> randomizeInstance(idRandomizer, json));
    tl.add("instances", INSTANCES);
    tl.add("bound-with/instances", INSTANCES);

    // Update instanceId references in related entities
    tl.withFilter(idRandomizer::updateInstanceIdReferences);
    tl.add("holdingsrecords", HOLDINGS);
    tl.add("items", ITEMS);
    tl.add("bound-with/holdingsrecords", HOLDINGS);
    tl.add("bound-with/items", ITEMS);
    tl.add("bound-with/bound-with-parts", BOUND_WITH_PARTS);
    tl.add("instance-relationships", INSTANCE_RELATIONSHIPS);
  }

  private Future<Void> runJavaMigrations(TenantAttributes ta, Context context,
                                         Map<String, String> okapiHeaders) {

    log.info("About to start java migrations...");

    var javaMigrations = List.<BaseMigrationService>of();

    var startedMigrations = javaMigrations.stream()
      .filter(javaMigration -> javaMigration.shouldExecuteMigration(ta))
      .map(BaseMigrationService::runMigration)
      .toList();

    return Future.all(startedMigrations)
      .onSuccess(notUsed -> log.info("Java migrations has been completed"))
      .onFailure(error -> log.error("Some java migrations failed", error))
      .mapEmpty();
  }

  private String randomizeInstance(SampleDataIdRandomizer idRandomizer, String json) {
    var randomizedIdJson = idRandomizer.randomizeInstanceId(json);
    return idRandomizer.randomizeHrid(randomizedIdJson);
  }
}
