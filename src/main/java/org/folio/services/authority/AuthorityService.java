package org.folio.services.authority;

import static io.vertx.core.Promise.promise;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.AuthorityRecordsAPI.AUTHORITY_TABLE;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AuthorityRepository;
import org.folio.rest.jaxrs.model.Authority;
import org.folio.rest.jaxrs.resource.AuthorityStorage;

public class AuthorityService {
  private static final Logger log = getLogger(AuthorityService.class);

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final AuthorityRepository authorityRepository;

  public AuthorityService(Context vertxContext,
                          Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    authorityRepository = new AuthorityRepository(vertxContext, okapiHeaders);
  }

  public Future<Response> createAuthority(Authority entity) {
    final Promise<Response> postResponse = promise();
    post(AUTHORITY_TABLE, entity, okapiHeaders, vertxContext,
      AuthorityStorage.PostAuthorityStorageAuthoritiesResponse.class, postResponse);
    return postResponse.future();
  }

  public Future<Response> updateAuthority(String authorityId,
                                          Authority newAuthority) {
    final Promise<Response> putResult = promise();
    put(AUTHORITY_TABLE, newAuthority, authorityId, okapiHeaders, vertxContext,
      AuthorityStorage.PutAuthorityStorageAuthoritiesByAuthorityIdResponse.class, putResult);
    return putResult.future();
  }

  public Future<Response> deleteAuthority(String authorityId) {
    final Promise<Response> deleteResult = promise();
    deleteById(AUTHORITY_TABLE, authorityId, okapiHeaders, vertxContext,
      AuthorityStorage.DeleteAuthorityStorageAuthoritiesByAuthorityIdResponse.class, deleteResult);
    return deleteResult.future();
  }

  public Future<Void> deleteAllAuthorities() {
    return authorityRepository.deleteAll()
      .compose(notUsed -> Future.succeededFuture());
  }
}
