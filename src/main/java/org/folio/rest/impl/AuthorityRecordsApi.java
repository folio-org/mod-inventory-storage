package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Authority;
import org.folio.rest.jaxrs.resource.AuthorityStorage;
import org.folio.rest.persist.PgUtil;
import org.folio.services.authority.AuthorityService;

public class AuthorityRecordsApi implements AuthorityStorage {

  public static final String AUTHORITY_TABLE = "authority";

  @Validate
  @Override
  public void getAuthorityStorageAuthorities(int offset, int limit,
                                             String query, String lang,
                                             RoutingContext routingContext,
                                             Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {
    PgUtil.streamGet(AUTHORITY_TABLE, Authority.class, query, offset, limit, null, "authorities",
      routingContext, okapiHeaders, vertxContext);
  }

  @Validate
  @Override
  public void postAuthorityStorageAuthorities(String lang, Authority entity,
                                              RoutingContext routingContext,
                                              Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
    new AuthorityService(vertxContext, okapiHeaders).createAuthority(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteAuthorityStorageAuthorities(String lang,
                                                RoutingContext routingContext,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    new AuthorityService(vertxContext, okapiHeaders).deleteAllAuthorities()
      .onSuccess(response -> asyncResultHandler
        .handle(succeededFuture(DeleteAuthorityStorageAuthoritiesResponse.respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getAuthorityStorageAuthoritiesByAuthorityId(String authorityId,
                                                          String lang,
                                                          Map<String, String> okapiHeaders,
                                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                                          Context vertxContext) {
    PgUtil.getById(AUTHORITY_TABLE, Authority.class, authorityId, okapiHeaders, vertxContext,
      GetAuthorityStorageAuthoritiesByAuthorityIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteAuthorityStorageAuthoritiesByAuthorityId(String authorityId,
                                                             String lang,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    new AuthorityService(vertxContext, okapiHeaders).deleteAuthority(authorityId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putAuthorityStorageAuthoritiesByAuthorityId(String authorityId,
                                                          String lang,
                                                          Authority entity,
                                                          Map<String, String> okapiHeaders,
                                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                                          Context vertxContext) {
    new AuthorityService(vertxContext, okapiHeaders).updateAuthority(authorityId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void postAuthorityStorageAuthoritiesBatch(String authoritiesFilePath, int batchSize,
                                                  Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    new AuthorityService(vertxContext, okapiHeaders).updateAuthorities(authoritiesFilePath, batchSize)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(Response.noContent().build())))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
