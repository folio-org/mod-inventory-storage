package org.folio.rest.impl;

import java.util.Arrays;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.tools.utils.TenantTool;

public class TenantRefAPI extends TenantAPI {

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
    "item-note-types",
    ""
  };

  final String[] samplePaths = new String[]{
    "instances instance-storage/instances",
    "instance-source-records instance-storage/instances/%d/source-record/marc-json",
    "holdingsrecords holdings-storage/holdings",
    "items instance-storage/instances",
    "instance-relationships instance-storage/instance-relationships",
    ""
  };

  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");
    Vertx vertx = cntxt.owner();
    super.postTenant(ta, headers, res -> {
      if (res.failed()) {
        hndlr.handle(res);
        return;
      }
      TenantTool.load(ta, headers, "loadReference", "ref-data",
        Arrays.asList(refPaths), vertx, res1 -> {
        if (res1.failed()) {
          hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
            .respond500WithTextPlain(res1.cause().getLocalizedMessage())));
          return;
        }
        TenantTool.load(ta, headers, "loadSample", "sample-data",
          Arrays.asList(samplePaths), vertx, res2 -> {
          if (res2.failed()) {
            hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond500WithTextPlain(res2.cause().getLocalizedMessage())));
            return;
          }
          hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
            .respond201WithApplicationJson("")));
        });
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
