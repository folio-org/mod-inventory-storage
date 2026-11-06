package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.folio.rest.jaxrs.model.ItemDamageStatuses;

public class ItemDamagedStatusApi extends BaseApi<ItemDamageStatus, ItemDamageStatuses>
  implements org.folio.rest.jaxrs.resource.ItemDamagedStatuses {

  public static final String ITEM_DAMAGED_STATUS_TABLE = "item_damaged_status";

  @Validate
  @Override
  public void getItemDamagedStatuses(String query, String totalRecords, int offset, int limit,
                                     Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postItemDamagedStatuses(ItemDamageStatus entity, Map<String, String> headers,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getItemDamagedStatusesById(String id, Map<String, String> headers,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    getEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteItemDamagedStatusesById(String id, Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    deleteEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putItemDamagedStatusesById(String id, ItemDamageStatus entity,
                                         Map<String, String> headers,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    putEntityById(id, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return ITEM_DAMAGED_STATUS_TABLE;
  }

  @Override
  protected Class<ItemDamageStatus> getEntityClass() {
    return ItemDamageStatus.class;
  }

  @Override
  protected Class<ItemDamageStatuses> getEntityCollectionClass() {
    return ItemDamageStatuses.class;
  }
}
