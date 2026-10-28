package org.folio.rest.impl;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.resource.InventoryStorageCustomFieldsCleanup;
import org.folio.services.customfields.CustomFieldsValueCleanup;

public class InventoryStorageCustomFieldsCleanupApi implements InventoryStorageCustomFieldsCleanup {

  @Override
  public void postInventoryStorageCustomFieldsCleanup(Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    CustomFieldsValueCleanup.kick(vertxContext.owner(), tenantId(okapiHeaders));
    asyncResultHandler.handle(Future.succeededFuture(PostInventoryStorageCustomFieldsCleanupResponse.respond204()));
  }
}
