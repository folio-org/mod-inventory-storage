package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.subjecttype.SubjectTypeService.SUBJECT_TYPE;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.SubjectType;

import java.util.Map;

public class SubjectTypeRepository extends AbstractRepository<SubjectType> {

  public SubjectTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), SUBJECT_TYPE, SubjectType.class);
  }
}
