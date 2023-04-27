package org.folio.persist;

import static org.folio.rest.impl.AuthoritySourceFileApi.AUTHORITY_SOURCE_FILE_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.AuthoritySourceFile;

public class AuthoritySourceFileRepository extends AbstractRepository<AuthoritySourceFile> {
  public AuthoritySourceFileRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), AUTHORITY_SOURCE_FILE_TABLE, AuthoritySourceFile.class);
  }
}
