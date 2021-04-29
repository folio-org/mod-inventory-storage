package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.MarcJson;

public class InstanceMarcRepository extends AbstractRepository<MarcJson> {
  public static final String INSTANCE_SOURCE_MARC_TABLE = "instance_source_marc";

  public InstanceMarcRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_SOURCE_MARC_TABLE, MarcJson.class);
  }
}
