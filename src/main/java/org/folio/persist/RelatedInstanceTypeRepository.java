package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.Map;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.RelatedInstanceType;

public class RelatedInstanceTypeRepository extends AbstractRepository<RelatedInstanceType> {
  public static final String RELATED_INSTANCE_TYPE_TABLE =  "related_instance_type";

  public RelatedInstanceTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), RELATED_INSTANCE_TYPE_TABLE, RelatedInstanceType.class);
  }

}
