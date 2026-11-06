package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectSourceRepository extends AbstractRepository<SubjectSource> {

  public static final String SUBJECT_SOURCE = "subject_source";

  public SubjectSourceRepository(Context context) {
    super(context, SUBJECT_SOURCE, SubjectSource.class);
  }
}
