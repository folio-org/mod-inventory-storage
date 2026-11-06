package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.springframework.stereotype.Repository;

@Repository
public class InstitutionRepository extends AbstractRepository<LocationInstitution> {

  public static final String INSTITUTION_TABLE = "locinstitution";

  public InstitutionRepository(Context context) {
    super(context, INSTITUTION_TABLE, LocationInstitution.class);
  }
}
