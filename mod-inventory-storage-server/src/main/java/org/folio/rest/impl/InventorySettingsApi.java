package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.SettingUpdateRequest;
import org.folio.rest.jaxrs.resource.InventorySettings;
import org.folio.services.setting.SettingsService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InventorySettingsApi implements InventorySettings {

  @Autowired
  private SettingsService settingsService;

  public InventorySettingsApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getInventorySettingsByKey(String key, Map<String, String> headers,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    settingsService.getSettingByKey(key, headers)
      .onSuccess(settingValue -> asyncResultHandler.handle(succeededFuture(
        GetInventorySettingsByKeyResponse.respond200WithApplicationJson(settingValue))))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void patchInventorySettingsByKey(String key, SettingUpdateRequest request,
                                          Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    settingsService.updateSetting(key, request.getValue(), headers)
      .onSuccess(notUsed -> asyncResultHandler.handle(succeededFuture(
        PatchInventorySettingsByKeyResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }
}


