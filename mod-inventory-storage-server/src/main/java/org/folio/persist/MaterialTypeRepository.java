package org.folio.persist;

import static org.folio.rest.impl.MaterialTypeApi.MATERIAL_TYPE_TABLE;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.MaterialType;
import org.springframework.stereotype.Repository;

@Repository
public class MaterialTypeRepository extends AbstractRepository<MaterialType> {

  public MaterialTypeRepository(Context context) {
    super(context, MATERIAL_TYPE_TABLE, MaterialType.class);
  }
}
