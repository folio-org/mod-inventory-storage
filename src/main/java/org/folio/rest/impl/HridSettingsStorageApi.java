package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.resource.HridSettingsStorage;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;

public class HridSettingsStorageApi implements HridSettingsStorage {
  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  public void getHridSettingsStorageHridSettings(Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        try {
          final PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
            TenantTool.tenantId(okapiHeaders));
          final HridManager hridManager = new HridManager(postgresClient);
          hridManager.getHridSettings()
            .map(hridSettings -> successGet(asyncResultHandler, hridSettings))
            .otherwise(error -> internalErrorGet(asyncResultHandler, error));
        } catch (Exception e) {
          internalErrorGet(asyncResultHandler, e);
        }
      });
    } catch (Exception e) {
      internalErrorGet(asyncResultHandler, e);
    }
  }

  @Validate
  @Override
  public void putHridSettingsStorageHridSettings(HridSettings hridSettings,
                                                 Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        try {
          final PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
          final HridManager hridManager = new HridManager(postgresClient);
          hridManager.updateHridSettings(hridSettings)
            .map(success -> successPut(asyncResultHandler))
            .otherwise(error -> internalErrorPut(asyncResultHandler, error));
        } catch (Exception e) {
          internalErrorPut(asyncResultHandler, e);
        }
      });
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
