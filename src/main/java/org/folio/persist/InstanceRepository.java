package org.folio.persist;

import static org.folio.rest.impl.InstanceStorageAPI.INSTANCE_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.Map;

import org.folio.rest.jaxrs.model.Instance;

import io.vertx.core.Context;

public class InstanceRepository extends AbstractRepository<Instance> {

  public InstanceRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_TABLE, Instance.class);
  }
}
