package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TenantRefAPI extends TenantAPI {

  private static final String SAMPLE_LEAD = "sample-data";
  private static final String SAMPLE_KEY = "loadSample";
  private static final String REFERENCE_KEY = "loadReference";
  private static final String REFERENCE_LEAD = "ref-data";

  private static final Logger log = LogManager.getLogger();
  final String[] refPaths = new String[]{
    "material-types",
    "loan-types",
    "location-units/institutions",
    "location-units/campuses",
    "location-units/libraries",
    "locations",
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
    "instance-note-types",
    "holdings-note-types",
    "item-note-types",
    "item-damaged-statuses",
    "holdings-sources"
  };

  List<JsonObject> servicePoints = null;

  String servicePointUserFilter(String s) {
    JsonObject jInput = new JsonObject(s);
    JsonObject jOutput = new JsonObject();
    jOutput.put("userId", jInput.getString("id"));
    JsonArray ar = new JsonArray();
    for (JsonObject pt : servicePoints) {
      ar.add(pt.getString("id"));
    }
    jOutput.put("servicePointsIds", ar);
    jOutput.put("defaultServicePointId", ar.getString(0));
    String res = jOutput.encodePrettily();
    log.info("servicePointUser result : " + res);
    return res;
  }

  @Validate
  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
                           Map<String, String> headers, Context vertxContext) {
    return super.loadData(attributes, tenantId, headers, vertxContext)
        .compose(superRecordsLoaded -> {
          try {
            List<URL> urls = TenantLoading.getURLsFromClassPathDir(
                REFERENCE_LEAD + "/service-points");
            servicePoints = new LinkedList<>();
            for (URL url : urls) {
              InputStream stream = url.openStream();
              String content = IOUtils.toString(stream, StandardCharsets.UTF_8);
              stream.close();
              servicePoints.add(new JsonObject(content));
            }
          } catch (URISyntaxException | IOException ex) {
            return Future.failedFuture(ex.getMessage());
          }
          TenantLoading tl = new TenantLoading();

          tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD);
          tl.withIdContent();
          for (String p : refPaths) {
            tl.add(p);
          }
          tl.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD);
          tl.add("instances", "instance-storage/instances");
          tl.withIdContent();
          tl.add("holdingsrecords", "holdings-storage/holdings");
          tl.add("items", "item-storage/items");
          tl.add("instance-relationships", "instance-storage/instance-relationships");
          if (servicePoints != null) {
            tl.withFilter(this::servicePointUserFilter)
                .withPostOnly()
                .withAcceptStatus(422)
                .add("users", "service-points-users");
          }
          return tl.perform(attributes, headers, vertxContext, superRecordsLoaded);
        });
  }
}
