package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AuthoritySourceFile;
import org.folio.rest.jaxrs.model.AuthoritySourceFiles;
import org.folio.rest.persist.PgUtil;

public class AuthoritySourceFileAPI implements org.folio.rest.jaxrs.resource.AuthoritySourceFiles {

  public static final String REFERENCE_TABLE = "authority_source_file";

  public static final String ULR_PROTOCOL_PATTERN = "^(http[s]?://www\\.|http[s]?://|www\\.)";

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
    normalizeBaseUrl(entity);

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
    PgUtil.deleteById(REFERENCE_TABLE, id, okapiHeaders, vertxContext,
      DeleteAuthoritySourceFilesByIdResponse.class,
      asyncResultHandler);
  }

  @Override
  @Validate
  public void putAuthoritySourceFilesById(String id, String lang,
                                          AuthoritySourceFile entity,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    normalizeBaseUrl(entity);

    PgUtil.put(REFERENCE_TABLE, entity, id, okapiHeaders, vertxContext,
      PutAuthoritySourceFilesByIdResponse.class,
      asyncResultHandler);
  }

  private static void normalizeBaseUrl(AuthoritySourceFile entity) {
    var baseUrl = entity.getBaseUrl();
    if (StringUtils.isNotBlank(baseUrl)) {
      baseUrl = baseUrl.replaceFirst(ULR_PROTOCOL_PATTERN, "");
      if (!baseUrl.endsWith("/")) {
        baseUrl += "/";
      }
      entity.setBaseUrl(baseUrl);
    }
  }

}
