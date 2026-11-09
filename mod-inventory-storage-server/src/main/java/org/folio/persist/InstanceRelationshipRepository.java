package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.springframework.stereotype.Repository;

@Repository
public class InstanceRelationshipRepository extends AbstractRepository<InstanceRelationship> {

  public static final String INSTANCE_RELATIONSHIP_TABLE = "instance_relationship";

  public InstanceRelationshipRepository(Context context) {
    super(context, INSTANCE_RELATIONSHIP_TABLE, InstanceRelationship.class);
  }
}
