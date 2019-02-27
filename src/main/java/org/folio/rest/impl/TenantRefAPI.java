package org.folio.rest.impl;

import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.tools.utils.TenantLoading;

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
    "classification-types",
    "instance-statuses",
    "statistical-code-types", "statistical-codes",
    "modes-of-issuance",
    "alternative-title-types",
    "electronic-access-relationships",
    "ill-policies",
    "holdings-types",
    "call-number-types",
    "holdings-note-types",
    "item-note-types"
  };

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
      TenantLoading tl = new TenantLoading();
      for (String p : refPaths) {
        tl.addJsonIdContent(REFERENCE_KEY, REFERENCE_LEAD, p, p);
      }
      tl.addJsonIdContent(SAMPLE_KEY, SAMPLE_LEAD, "instances", "instance-storage/instances");
      tl.addJsonIdBasename(SAMPLE_KEY, SAMPLE_LEAD,
        "instance-storages/instances/%d/source-record/marc-json", "instance-source-records");
      tl.addJsonIdContent(SAMPLE_KEY, SAMPLE_LEAD, "holdingsrecords", "holdings-storage/holdings");
      tl.addJsonIdContent(SAMPLE_KEY, SAMPLE_LEAD, "items", "item-storage/items");
      tl.addJsonIdContent(SAMPLE_KEY, SAMPLE_LEAD, "instance-relationships",
        "instance-storage/instance-relationships");
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
