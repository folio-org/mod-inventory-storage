package org.folio.rest.impl;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.resource.HridSettingsStorage;
import org.folio.services.HridManager;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class HridSettingsStorageApi implements HridSettingsStorage {

  private static final Logger log = LogManager.getLogger();

  @Autowired
  private HridManager hridManager;

  public HridSettingsStorageApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getHridSettingsStorageHridSettings(Map<String, String> headers,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    try {
      hridManager.getHridSettings(tenantId(headers))
        .map(hridSettings -> successGet(asyncResultHandler, hridSettings))
        .otherwise(error -> internalErrorGet(asyncResultHandler, error));
    } catch (Exception e) {
      internalErrorGet(asyncResultHandler, e);
    }
  }

  @Validate
  @Override
  public void putHridSettingsStorageHridSettings(HridSettings hridSettings,
                                                 Map<String, String> headers,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    try {
      hridManager.updateHridSettings(hridSettings, tenantId(headers))
        .map(success -> successPut(asyncResultHandler))
        .otherwise(error -> internalErrorPut(asyncResultHandler, error));
    } catch (Exception e) {
      internalErrorPut(asyncResultHandler, e);
    }
  }

  private Void successGet(Handler<AsyncResult<Response>> asyncResultHandler,
                          HridSettings hridSettings) {
    asyncResultHandler.handle(
      Future.succeededFuture(GetHridSettingsStorageHridSettingsResponse
        .respond200WithApplicationJson(hridSettings)));
    return null;
  }

  private Void internalErrorGet(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error("Internal error during GET", e);
    asyncResultHandler.handle(Future.succeededFuture(
      GetHridSettingsStorageHridSettingsResponse.respond500WithTextPlain(e.getMessage())));
    return null;
  }

  private Void successPut(Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(Future.succeededFuture(
      PutHridSettingsStorageHridSettingsResponse.respond204()));
    return null;
  }

  private Void internalErrorPut(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    log.error("Internal error during PUT", e);
    asyncResultHandler.handle(Future.succeededFuture(
      PutHridSettingsStorageHridSettingsResponse.respond500WithTextPlain(e.getMessage())));
    return null;
  }
}
