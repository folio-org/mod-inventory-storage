package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.InstanceRelationship;

public class InstanceRelationshipRepository extends AbstractRepository<InstanceRelationship> {
  public static final String INSTANCE_RELATIONSHIP_TABLE = "instance_relationship";

  public InstanceRelationshipRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_RELATIONSHIP_TABLE,
      InstanceRelationship.class);
  }
}
