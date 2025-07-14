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
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetItemDamagedStatusesResponse.class);
  }

  @Validate
  @Override
  public void postItemDamagedStatuses(ItemDamageStatus entity, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostItemDamagedStatusesResponse.class);
  }

  @Validate
  @Override
  public void getItemDamagedStatusesById(String id, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetItemDamagedStatusesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteItemDamagedStatusesById(String id, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteItemDamagedStatusesByIdResponse.class);
  }

  @Validate
  @Override
  public void putItemDamagedStatusesById(String id, ItemDamageStatus entity,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext, PutItemDamagedStatusesByIdResponse.class);
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
