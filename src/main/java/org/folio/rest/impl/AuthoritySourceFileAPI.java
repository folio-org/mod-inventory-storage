package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AuthoritySourceFile;
import org.folio.rest.jaxrs.model.AuthoritySourceFiles;
import org.folio.rest.persist.PgUtil;

public class AuthoritySourceFileAPI implements org.folio.rest.jaxrs.resource.AuthoritySourceFiles {
  public static final String REFERENCE_TABLE = "authority_source_file";

  public static final Set<String> PRE_DEFINED_SOURCE_FILE_IDS =
    Set.of(
      "cb58492d-018e-442d-9ce3-35aabfc524aa",
      "191874a0-707a-4634-928e-374ee9103225",
      "b224845c-5026-4594-8b55-61d39ecf0541",
      "ccebe5d8-5bfe-46f5-bfa2-79f257c249c9",
      "4b531a84-d4fe-44e5-b75f-542ec71b2f62",
      "67d1ec4b-a19a-4324-9f19-473b49e370ac",
      "2c0e41b5-8ffb-4856-aa64-76648a6f6b18",
      "af045f2f-e851-4613-984c-4bc13430454a",
      "837e2c7b-037b-4113-9dfd-b1b8aeeb1fb8",
      "6ddf21a6-bc2f-4cb0-ad96-473e1f82da23",
      "b0f38dbe-5bc0-477d-b1ee-6d9878a607f7",
      "70ff583b-b8c9-483e-ac21-cb4a9217898b"
    );

  public static final String UPDATE_PRE_DEFINED_SOURCE_FILE_ERROR = "Modification of pre-defined authority source file is not allowed";
  public static final String DELETE_PRE_DEFINED_SOURCE_FILE_ERROR = "Deletion of pre-defined authority source file is not allowed";

  @Override
  @Validate
  public void getAuthoritySourceFiles(String query, int offset, int limit,
                                      String lang,
                                      Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    PgUtil.get(REFERENCE_TABLE, AuthoritySourceFile.class,
      AuthoritySourceFiles.class, query, offset, limit, okapiHeaders,
      vertxContext, GetAuthoritySourceFilesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postAuthoritySourceFiles(String lang, AuthoritySourceFile entity,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    PgUtil.post(REFERENCE_TABLE, entity, okapiHeaders, vertxContext,
      PostAuthoritySourceFilesResponse.class,
      asyncResultHandler);
  }

  @Override
  @Validate
  public void getAuthoritySourceFilesById(String id, String lang,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, AuthoritySourceFile.class, id,
      okapiHeaders, vertxContext, GetAuthoritySourceFilesByIdResponse.class,
      asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAuthoritySourceFilesById(String id, String lang,
                                             Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {
    if (!PRE_DEFINED_SOURCE_FILE_IDS.contains(id)) {
      PgUtil.deleteById(REFERENCE_TABLE, id, okapiHeaders, vertxContext,
        DeleteAuthoritySourceFilesByIdResponse.class,
        asyncResultHandler);
    } else {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteAuthoritySourceFilesByIdResponse.
          respond400WithTextPlain(DELETE_PRE_DEFINED_SOURCE_FILE_ERROR)));
    }
  }

  @Override
  @Validate
  public void putAuthoritySourceFilesById(String id, String lang,
                                          AuthoritySourceFile entity,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    if (!PRE_DEFINED_SOURCE_FILE_IDS.contains(id)) {
      PgUtil.put(REFERENCE_TABLE, entity, id, okapiHeaders, vertxContext,
        PutAuthoritySourceFilesByIdResponse.class,
        asyncResultHandler);
    } else {
      asyncResultHandler.handle(Future.succeededFuture(
        PutAuthoritySourceFilesByIdResponse
          .respond400WithTextPlain(UPDATE_PRE_DEFINED_SOURCE_FILE_ERROR)));
    }
  }
}
