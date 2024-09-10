package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.subjecttype.SubjectTypeService.SUBJECT_TYPE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.SubjectType;

public class SubjectTypeRepository extends AbstractRepository<SubjectType> {

  public SubjectTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), SUBJECT_TYPE, SubjectType.class);
  }
}
