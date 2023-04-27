package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AuthorityNoteType;
import org.folio.rest.jaxrs.model.AuthorityNoteTypes;
import org.folio.rest.persist.PgUtil;

public class AuthorityNoteTypeApi implements org.folio.rest.jaxrs.resource.AuthorityNoteTypes {
  public static final String REFERENCE_TABLE = "authority_note_type";

  @Override
  @Validate
  public void getAuthorityNoteTypes(String query, int offset, int limit, String lang,
                                    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                    Context vertxContext) {
    PgUtil.get(REFERENCE_TABLE, AuthorityNoteType.class, AuthorityNoteTypes.class, query, offset, limit, okapiHeaders,
      vertxContext, GetAuthorityNoteTypesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postAuthorityNoteTypes(String lang, AuthorityNoteType entity, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(REFERENCE_TABLE, entity, okapiHeaders, vertxContext, PostAuthorityNoteTypesResponse.class,
      asyncResultHandler);
  }

  @Override
  @Validate
  public void getAuthorityNoteTypesById(String id, String lang, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, AuthorityNoteType.class, id,
      okapiHeaders, vertxContext, GetAuthorityNoteTypesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAuthorityNoteTypesById(String id, String lang, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(REFERENCE_TABLE, id, okapiHeaders, vertxContext, DeleteAuthorityNoteTypesByIdResponse.class,
      asyncResultHandler);
  }

  @Override
  @Validate
  public void putAuthorityNoteTypesById(String id, String lang, AuthorityNoteType entity,
                                        Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REFERENCE_TABLE, entity, id, okapiHeaders, vertxContext, PutAuthorityNoteTypesByIdResponse.class,
      asyncResultHandler);
  }
}
