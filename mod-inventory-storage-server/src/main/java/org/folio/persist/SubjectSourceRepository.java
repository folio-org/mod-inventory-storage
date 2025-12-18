package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.subjectsource.SubjectSourceService.SUBJECT_SOURCE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.SubjectSource;

public class SubjectSourceRepository extends AbstractRepository<SubjectSource> {

  public SubjectSourceRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), SUBJECT_SOURCE, SubjectSource.class);
  }
}
