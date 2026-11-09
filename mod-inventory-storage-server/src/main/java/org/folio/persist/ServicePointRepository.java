package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.springframework.stereotype.Repository;

@Repository
public class ServicePointRepository extends AbstractRepository<ServicePoint> {

  private static final String SERVICE_POINT_TABLE = "service_point";

  public ServicePointRepository(Context context) {
    super(context, SERVICE_POINT_TABLE, ServicePoint.class);
  }
}
