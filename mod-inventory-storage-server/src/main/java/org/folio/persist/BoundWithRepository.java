package org.folio.persist;

import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.springframework.stereotype.Repository;

@Repository
public class BoundWithRepository extends AbstractRepository<BoundWithPart> {

  public BoundWithRepository(Context context) {
    super(context, BOUND_WITH_TABLE, BoundWithPart.class);
  }
}
