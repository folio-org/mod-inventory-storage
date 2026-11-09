package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.springframework.stereotype.Repository;

@Repository
public class ClassificationTypeRepository extends AbstractRepository<ClassificationType> {

  public static final String CLASSIFICATION_TYPE_TABLE = "classification_type";

  public ClassificationTypeRepository(Context context) {
    super(context, CLASSIFICATION_TYPE_TABLE, ClassificationType.class);
  }
}
