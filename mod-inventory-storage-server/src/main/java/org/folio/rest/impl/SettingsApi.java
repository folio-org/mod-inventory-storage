package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.SettingUpdateRequest;
import org.folio.rest.jaxrs.resource.Settings;
import org.folio.services.setting.SettingsService;

public class SettingsApi implements Settings {

  @Override
  public void getSettingsByKey(String key, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    new SettingsService(vertxContext, okapiHeaders)
      .getSettingByKey(key)
      .onSuccess(settingValue -> asyncResultHandler.handle(succeededFuture(
        GetSettingsByKeyResponse.respond200WithApplicationJson(settingValue))))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void patchSettingsByKey(String key, SettingUpdateRequest request,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    new SettingsService(vertxContext, okapiHeaders)
      .updateSetting(key, request.getValue(), okapiHeaders)
      .onSuccess(notUsed -> asyncResultHandler.handle(succeededFuture(
        PatchSettingsByKeyResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }
}


