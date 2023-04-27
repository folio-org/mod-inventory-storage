package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.StringUtils;
import org.folio.persist.AuthoritySourceFileRepository;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AuthoritySourceFile;
import org.folio.rest.jaxrs.model.AuthoritySourceFilePatchRequest;
import org.folio.rest.jaxrs.model.AuthoritySourceFiles;
import org.folio.rest.persist.PgUtil;
import org.folio.validator.CommonValidators;

public class AuthoritySourceFileApi implements org.folio.rest.jaxrs.resource.AuthoritySourceFiles {

  public static final String AUTHORITY_SOURCE_FILE_TABLE = "authority_source_file";

  private static final String URL_PROTOCOL_PATTERN = "^(https?://www\\.|https?://|www\\.)";

  private static void normalizeBaseUrl(AuthoritySourceFile entity) {
    var baseUrl = entity.getBaseUrl();
    if (StringUtils.isNotBlank(baseUrl)) {
      baseUrl = baseUrl.replaceFirst(URL_PROTOCOL_PATTERN, "");
      if (!baseUrl.endsWith("/")) {
        baseUrl += "/";
      }
      entity.setBaseUrl(baseUrl);
    }
  }

  @Override
  @Validate
  public void getAuthoritySourceFiles(String query, int offset, int limit,
                                      String lang,
                                      Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    PgUtil.get(AUTHORITY_SOURCE_FILE_TABLE, AuthoritySourceFile.class,
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

    PgUtil.post(AUTHORITY_SOURCE_FILE_TABLE, entity, okapiHeaders, vertxContext,
      PostAuthoritySourceFilesResponse.class,
      asyncResultHandler);
  }

  @Override
  @Validate
  public void patchAuthoritySourceFilesById(String id,
                                            AuthoritySourceFilePatchRequest patchData,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {
    new AuthoritySourceFileRepository(vertxContext, okapiHeaders).getById(id)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(entity -> {
        entity.setBaseUrl(patchData.getBaseUrl());

        normalizeBaseUrl(entity);

        return put(AUTHORITY_SOURCE_FILE_TABLE, entity, id, okapiHeaders, vertxContext,
          PatchAuthoritySourceFilesByIdResponse.class);
      })
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  @Validate
  public void getAuthoritySourceFilesById(String id, String lang,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    PgUtil.getById(AUTHORITY_SOURCE_FILE_TABLE, AuthoritySourceFile.class, id,
      okapiHeaders, vertxContext, GetAuthoritySourceFilesByIdResponse.class,
      asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteAuthoritySourceFilesById(String id, String lang,
                                             Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {
    PgUtil.deleteById(AUTHORITY_SOURCE_FILE_TABLE, id, okapiHeaders, vertxContext,
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

    PgUtil.put(AUTHORITY_SOURCE_FILE_TABLE, entity, id, okapiHeaders, vertxContext,
      PutAuthoritySourceFilesByIdResponse.class,
      asyncResultHandler);
  }

}
