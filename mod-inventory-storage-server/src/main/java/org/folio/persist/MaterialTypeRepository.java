package org.folio.persist;

import static org.folio.rest.impl.MaterialTypeApi.MATERIAL_TYPE_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.MaterialType;

public class MaterialTypeRepository extends AbstractRepository<MaterialType> {

  public MaterialTypeRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), MATERIAL_TYPE_TABLE, MaterialType.class);
  }
}
