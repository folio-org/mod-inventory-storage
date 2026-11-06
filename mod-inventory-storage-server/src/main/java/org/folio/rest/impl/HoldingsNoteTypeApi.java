package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsNoteType;
import org.folio.rest.jaxrs.model.HoldingsNoteTypes;

public class HoldingsNoteTypeApi extends BaseApi<HoldingsNoteType, HoldingsNoteTypes>
  implements org.folio.rest.jaxrs.resource.HoldingsNoteTypes {

  public static final String HOLDINGS_NOTE_TYPE_TABLE = "holdings_note_type";

  @Validate
  @Override
  public void getHoldingsNoteTypes(String query, String totalRecords, int offset, int limit,
                                   Map<String, String> headers,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postHoldingsNoteTypes(HoldingsNoteType entity, Map<String, String> headers,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getHoldingsNoteTypesById(String id, Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteHoldingsNoteTypesById(String id, Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putHoldingsNoteTypesById(String id, HoldingsNoteType entity,
                                       Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return HOLDINGS_NOTE_TYPE_TABLE;
  }

  @Override
  protected Class<HoldingsNoteType> getEntityClass() {
    return HoldingsNoteType.class;
  }

  @Override
  protected Class<HoldingsNoteTypes> getEntityCollectionClass() {
    return HoldingsNoteTypes.class;
  }
}
