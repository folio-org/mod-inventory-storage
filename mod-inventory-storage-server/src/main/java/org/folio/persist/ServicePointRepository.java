package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.ServicePoint;

public class ServicePointRepository extends AbstractRepository<ServicePoint> {

  private static final String SERVICE_POINT_TABLE = "service_point";

  public ServicePointRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), SERVICE_POINT_TABLE, ServicePoint.class);
  }
}
