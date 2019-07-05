package org.folio.rest.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TenantRefAPI extends TenantAPI {

  private static final String SAMPLE_LEAD = "sample-data";
  private static final String SAMPLE_KEY = "loadSample";
  private static final String REFERENCE_KEY = "loadReference";
  private static final String REFERENCE_LEAD = "ref-data";

  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);
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
    "item-note-types"
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

  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> headers,
    Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");
    Vertx vertx = cntxt.owner();
    super.postTenant(ta, headers, res -> {
      if (res.failed()) {
        hndlr.handle(res);
        return;
      }
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
        hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond500WithTextPlain(ex.getLocalizedMessage())));
        return;
      }
      TenantLoading tl = new TenantLoading();

      tl.withKey(REFERENCE_KEY).withLead(REFERENCE_LEAD);
      tl.withIdContent();
      for (String p : refPaths) {
        tl.add(p);
      }
      tl.withKey(SAMPLE_KEY).withLead(SAMPLE_LEAD);
      tl.add("instances", "instance-storage/instances");
      tl.withIdBasename().add("instance-storages/instances/%d/source-record/marc-json",
        "instance-source-records");
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
      tl.perform(ta, headers, vertx, res1 -> {
        if (res1.failed()) {
          hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
            .respond500WithTextPlain(res1.cause().getLocalizedMessage())));
          return;
        }
        hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond201WithApplicationJson("")));
      });
    }, cntxt);
  }

  @Override
  public void getTenant(Map<String, String> map, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("getTenant");
    super.getTenant(map, hndlr, cntxt);
  }

  @Override
  public void deleteTenant(Map<String, String> map, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenant(map, hndlr, cntxt);
  }
}
