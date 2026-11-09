package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.MarcJson;
import org.springframework.stereotype.Repository;

@Repository
public class InstanceMarcRepository extends AbstractRepository<MarcJson> {

  public static final String INSTANCE_SOURCE_MARC_TABLE = "instance_source_marc";

  public InstanceMarcRepository(Context context) {
    super(context, INSTANCE_SOURCE_MARC_TABLE, MarcJson.class);
  }
}
