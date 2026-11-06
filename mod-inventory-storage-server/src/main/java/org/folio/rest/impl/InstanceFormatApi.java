package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceFormat;
import org.folio.rest.jaxrs.model.InstanceFormats;

/**
 * Implements the instance format persistency using postgres jsonb.
 */
public class InstanceFormatApi extends BaseApi<InstanceFormat, InstanceFormats>
  implements org.folio.rest.jaxrs.resource.InstanceFormats {

  public static final String INSTANCE_FORMAT_TABLE = "instance_format";

  @Validate
  @Override
  public void getInstanceFormats(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postInstanceFormats(InstanceFormat entity, Map<String, String> headers,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceFormatsById(String id,
                                     Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {
    getEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceFormatsById(String id,
                                        Map<String, String> headers,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    deleteEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putInstanceFormatsById(String id, InstanceFormat entity,
                                     Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {
    putEntityById(id, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return INSTANCE_FORMAT_TABLE;
  }

  @Override
  protected Class<InstanceFormat> getEntityClass() {
    return InstanceFormat.class;
  }

  @Override
  protected Class<InstanceFormats> getEntityCollectionClass() {
    return InstanceFormats.class;
  }
}
