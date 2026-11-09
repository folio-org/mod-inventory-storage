package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.SubjectType;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectTypeRepository extends AbstractRepository<SubjectType> {

  public static final String SUBJECT_TYPE = "subject_type";

  public SubjectTypeRepository(Context context) {
    super(context, SUBJECT_TYPE, SubjectType.class);
  }
}
