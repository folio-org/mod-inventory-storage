package org.folio.persist;

import static org.folio.rest.impl.ServicePointsUserApi.SERVICE_POINT_USER_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.ServicePointsUser;

public class ServicePointsUserRepository extends AbstractRepository<ServicePointsUser> {

  public ServicePointsUserRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), SERVICE_POINT_USER_TABLE, ServicePointsUser.class);
  }
}
