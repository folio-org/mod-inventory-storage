package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.rest.jaxrs.model.NatureOfContentTerms;

public class NatureOfContentTermApi extends BaseApi<NatureOfContentTerm, NatureOfContentTerms>
  implements org.folio.rest.jaxrs.resource.NatureOfContentTerms {

  public static final String NATURE_OF_CONTENT_TERM_TABLE = "nature_of_content_term";

  @Validate
  @Override
  public void getNatureOfContentTerms(String query, String totalRecords, int offset, int limit,
                                      Map<String, String> headers,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntities(query, totalRecords, offset, limit, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void postNatureOfContentTerms(NatureOfContentTerm entity, Map<String, String> headers,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    postEntity(entity, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void getNatureOfContentTermsById(String id, Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteNatureOfContentTermsById(String id, Map<String, String> headers,
                                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    deleteEntityById(id, headers, asyncResultHandler);
  }

  @Validate
  @Override
  public void putNatureOfContentTermsById(String id, NatureOfContentTerm entity,
                                          Map<String, String> headers,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    putEntityById(id, entity, headers, asyncResultHandler);
  }

  @Override
  protected String getReferenceTable() {
    return NATURE_OF_CONTENT_TERM_TABLE;
  }

  @Override
  protected Class<NatureOfContentTerm> getEntityClass() {
    return NatureOfContentTerm.class;
  }

  @Override
  protected Class<NatureOfContentTerms> getEntityCollectionClass() {
    return NatureOfContentTerms.class;
  }
}
