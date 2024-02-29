package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.classification.ClassificationTypeService.CLASSIFICATION_TYPE_TABLE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.ClassificationType;

public class ClassificationTypeRepository extends AbstractRepository<ClassificationType> {

  public ClassificationTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), CLASSIFICATION_TYPE_TABLE, ClassificationType.class);
  }

}
