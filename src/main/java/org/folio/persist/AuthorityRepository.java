package org.folio.persist;

import static org.folio.rest.impl.AuthorityRecordsApi.AUTHORITY_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.Authority;

public class AuthorityRepository extends AbstractRepository<Authority> {
  public AuthorityRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), AUTHORITY_TABLE, Authority.class);
  }
}
