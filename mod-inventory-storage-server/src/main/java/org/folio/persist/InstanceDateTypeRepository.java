package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.springframework.stereotype.Repository;

@Repository
public class InstanceDateTypeRepository extends AbstractRepository<InstanceDateType> {

  public static final String INSTANCE_DATE_TYPE_TABLE = "instance_date_type";

  public InstanceDateTypeRepository(Context context) {
    super(context, INSTANCE_DATE_TYPE_TABLE, InstanceDateType.class);
  }
}
