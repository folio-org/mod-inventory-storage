package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.ServicePointsUser;

public class ServicePointsUserRepository extends AbstractRepository<ServicePointsUser> {

  public static final String SERVICE_POINT_USER_TABLE = "service_point_user";

  public ServicePointsUserRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), SERVICE_POINT_USER_TABLE, ServicePointsUser.class);
  }
}
