package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.InventoryKafkaTopic;
import org.folio.dbschema.Versioned;
import org.folio.kafka.services.KafkaAdminClientService;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.services.migration.BaseMigrationService;
import org.folio.services.migration.item.ItemShelvingOrderMigrationService;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

public class TenantRefAPI extends TenantAPI {

  private static final String SAMPLE_LEAD = "sample-data";
  private static final String SAMPLE_KEY = "loadSample";
  private static final String REFERENCE_KEY = "loadReference";
  private static final String REFERENCE_LEAD = "ref-data";
  private static final String INSTANCES = "instance-storage/instances";
  private static final String HOLDINGS = "holdings-storage/holdings";
  private static final String ITEMS = "item-storage/items";
  private static final String INSTANCE_RELATIONSHIPS = "instance-storage/instance-relationships";
  private static final String BOUND_WITH_PARTS = "inventory-storage/bound-with-parts";
  private static final String SERVICE_POINTS_USERS = "service-points-users";

  private static final Logger log = LogManager.getLogger();
  final String[] refPaths = new String[]{
    "material-types",
    "loan-types",
    "identifier-types",
    "contributor-types",
    "service-points",
    "instance-relationship-types",
    "contributor-name-types",
    "instance-types",
    "instance-formats",
    "nature-of-content-terms",
    "classification-types",
    "instance-statuses",
    "statistical-code-types", "statistical-codes",
    "modes-of-issuance",
    "alternative-title-types",
    "electronic-access-relationships",
    "ill-policies",
    "holdings-types",
    "call-number-types",
    "authority-note-types",
    "instance-note-types",
    "holdings-note-types",
    "item-note-types",
    "item-damaged-statuses",
    "holdings-sources",
    "bound-with/instances",
    "bound-with/holdingsrecords",
    "bound-with/items",
    "bound-with/bound-with-parts",
    "authority-source-files"
  };

  /**
   * Returns attributes.getModuleFrom() < featureVersion or attributes.getModuleFrom() is null.
   */
  static boolean isNew(TenantAttributes attributes, String featureVersion) {
    if (attributes.getModuleFrom() == null) {
      return true;
    }
    var since = new Versioned() {
    };
    since.setFromModuleVersion(featureVersion);
    return since.isNewForThisInstall(attributes.getModuleFrom());
  }

  @Validate
  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
                           Map<String, String> headers, Context vertxContext) {

    // create topics before loading data
    Future<Integer> future = new KafkaAdminClientService(vertxContext.owner())
      .createKafkaTopics(InventoryKafkaTopic.values(), tenantId)
      .compose(x -> super.loadData(attributes, tenantId, headers, vertxContext));

    if (isNew(attributes, "20.0.0")) {
      List<JsonObject> servicePoints = new LinkedList<>();
      try {
        List<URL> urls = TenantLoading.getURLsFromClassPathDir(
          REFERENCE_LEAD + "/service-points");
        for (URL url : urls) {
          InputStream stream = url.openStream();
          String content = IOUtils.toString(stream, StandardCharsets.UTF_8);
          stream.close();
          servicePoints.add(new JsonObject(content));
        }
      } catch (URISyntaxException | IOException ex) {
        return Future.failedFuture(ex);
      }
      TenantLoading tl = new TenantLoading();

      tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD);
      tl.withIdContent();
      for (String p : refPaths) {
        tl.add(p);
      }
      tl.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD);
      tl.withIdContent();
      tl.add("location-units/institutions");
      tl.add("location-units/campuses");
      tl.add("location-units/libraries");
      tl.add("locations");
      tl.add("instances", INSTANCES);
      tl.add("holdingsrecords", HOLDINGS);
      tl.add("items", ITEMS);
      tl.add("instance-relationships", INSTANCE_RELATIONSHIPS);
      tl.add("bound-with/instances", INSTANCES);
      tl.add("bound-with/holdingsrecords", HOLDINGS);
      tl.add("bound-with/items", ITEMS);
      tl.add("bound-with/bound-with-parts", BOUND_WITH_PARTS);
      tl.withFilter(service -> servicePointUserFilter(service, servicePoints))
        .withPostOnly()
        .withAcceptStatus(422)
        .add("users", SERVICE_POINTS_USERS);
      future = future.compose(n -> tl.perform(attributes, headers, vertxContext, n));
    }

    if (isNew(attributes, "25.1.0")) {
      TenantLoading tl = new TenantLoading();
      tl.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD);
      tl.withIdContent();
      tl.add("bound-with/instances-25.1", INSTANCES);
      tl.add("bound-with/holdingsrecords-25.1", HOLDINGS);
      tl.add("bound-with/items-25.1", ITEMS);
      tl.add("bound-with/bound-with-parts-25.1", BOUND_WITH_PARTS);
      future = future.compose(n -> tl.perform(attributes, headers, vertxContext, n));
    }

    return future.compose(result -> runJavaMigrations(attributes, vertxContext, headers)
      .map(result));
  }

  @Validate
  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
                         Handler<AsyncResult<Response>> handler, Context context) {
    // delete Kafka topics if tenant purged
    Future<Void> result = tenantAttributes.getPurge() != null && tenantAttributes.getPurge()
      ? new KafkaAdminClientService(context.owner()).deleteKafkaTopics(InventoryKafkaTopic.values(), tenantId(headers))
      : Future.succeededFuture();
    result.onComplete(x -> super.postTenant(tenantAttributes, headers, handler, context));
  }

  private Future<Void> runJavaMigrations(TenantAttributes ta, Context context,
                                         Map<String, String> okapiHeaders) {

    log.info("About to start java migrations...");

    List<BaseMigrationService> javaMigrations = List.of(
      new ItemShelvingOrderMigrationService(context, okapiHeaders));

    var startedMigrations = javaMigrations.stream()
      .filter(javaMigration -> javaMigration.shouldExecuteMigration(ta))
      .peek(migration -> log.info(
        "Following migration is to be executed [migration={}]", migration))
      .map(BaseMigrationService::runMigration)
      .collect(Collectors.toList());

    return GenericCompositeFuture.all(startedMigrations)
      .onSuccess(notUsed -> log.info("Java migrations has been completed"))
      .onFailure(error -> log.error("Some java migrations failed", error))
      .mapEmpty();
  }

  private String servicePointUserFilter(String service, List<JsonObject> servicePoints) {
    JsonObject jInput = new JsonObject(service);
    JsonObject jOutput = new JsonObject();
    jOutput.put("userId", jInput.getString("id"));
    JsonArray ar = new JsonArray();
    for (JsonObject pt : servicePoints) {
      ar.add(pt.getString("id"));
    }
    jOutput.put("servicePointsIds", ar);
    jOutput.put("defaultServicePointId", ar.getString(0));
    String res = jOutput.encodePrettily();
    log.info("servicePointUser result : {}", res);
    return res;
  }
}
