package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceNoteType;
import org.folio.rest.jaxrs.model.InstanceNoteTypes;

public class InstanceNoteTypeApi extends BaseApi<InstanceNoteType, InstanceNoteTypes>
  implements org.folio.rest.jaxrs.resource.InstanceNoteTypes {

  public static final String REFERENCE_TABLE = "instance_note_type";

  @Validate
  @Override
  public void getInstanceNoteTypes(String query, String totalRecords, int offset, int limit,
                                   Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, okapiHeaders, asyncResultHandler, vertxContext,
      GetInstanceNoteTypesResponse.class);
  }

  @Validate
  @Override
  public void postInstanceNoteTypes(InstanceNoteType entity, Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, okapiHeaders, asyncResultHandler, vertxContext, PostInstanceNoteTypesResponse.class);
  }

  @Validate
  @Override
  public void getInstanceNoteTypesById(String id, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, GetInstanceNoteTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void deleteInstanceNoteTypesById(String id, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, okapiHeaders, asyncResultHandler, vertxContext, DeleteInstanceNoteTypesByIdResponse.class);
  }

  @Validate
  @Override
  public void putInstanceNoteTypesById(String id, InstanceNoteType entity,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, okapiHeaders, asyncResultHandler, vertxContext, PutInstanceNoteTypesByIdResponse.class);
  }

  @Override
  protected String getReferenceTable() {
    return REFERENCE_TABLE;
  }

  @Override
  protected Class<InstanceNoteType> getEntityClass() {
    return InstanceNoteType.class;
  }

  @Override
  protected Class<InstanceNoteTypes> getEntityCollectionClass() {
    return InstanceNoteTypes.class;
  }
}
