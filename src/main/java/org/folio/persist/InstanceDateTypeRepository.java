package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.instance.InstanceDateTypeService.INSTANCE_DATE_TYPE_TABLE;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.InstanceDateType;

public class InstanceDateTypeRepository extends AbstractRepository<InstanceDateType> {

  public InstanceDateTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_DATE_TYPE_TABLE, InstanceDateType.class);
  }
}
