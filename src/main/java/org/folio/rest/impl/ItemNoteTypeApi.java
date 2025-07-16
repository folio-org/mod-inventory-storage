package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ItemNoteType;
import org.folio.rest.jaxrs.model.ItemNoteTypes;

public class ItemNoteTypeApi extends BaseApi<ItemNoteType, ItemNoteTypes>
  implements org.folio.rest.jaxrs.resource.ItemNoteTypes {

  public static final String ITEM_NOTE_TYPE_TABLE = "item_note_type";

  @Validate
  @Override
  public void getItemNoteTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetItemNoteTypesResponse.class);
  }

  @Validate
  @Override
  public void postItemNoteTypes(ItemNoteType entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostItemNoteTypesResponse.class);
  }

  @Validate
  @Override
  public void getItemNoteTypesById(String id, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetItemNoteTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteItemNoteTypesById(String id, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteItemNoteTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void putItemNoteTypesById(String id, ItemNoteType entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext, PutItemNoteTypesByIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return ITEM_NOTE_TYPE_TABLE;
  }

  @Override
  protected Class<ItemNoteType> getEntityClass() {
    return ItemNoteType.class;
  }

  @Override
  protected Class<ItemNoteTypes> getEntityCollectionClass() {
    return ItemNoteTypes.class;
  }
}
