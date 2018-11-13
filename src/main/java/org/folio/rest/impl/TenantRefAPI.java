package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;

public class TenantRefAPI extends TenantAPI {
  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);

  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> map, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");
    super.postTenant(ta, map, hndlr, cntxt);
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
