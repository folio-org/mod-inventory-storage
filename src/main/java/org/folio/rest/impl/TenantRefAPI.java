package org.folio.rest.impl;

import java.util.Arrays;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.LinkedList;
import java.util.List;
import org.folio.rest.tools.utils.TenantTool;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);

  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");
    final String[] endPoints = new String[]{
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
    super.postTenant(ta, headers, res -> {
      if (res.failed()) {
        hndlr.handle(res);
        return;
      }
      TenantTool.load(ta, headers, "loadReference", "ref-data", Arrays.asList(endPoints), cntxt.owner(), res1 -> {
        if (res1.succeeded()) {
          hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
            .respond201WithApplicationJson("")));
        } else {
          hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
            .respond500WithTextPlain(res1.cause().getLocalizedMessage())));
        }
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
