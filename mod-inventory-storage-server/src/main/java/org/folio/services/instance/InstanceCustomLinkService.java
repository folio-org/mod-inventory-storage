package org.folio.services.instance;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.InstanceCustomLinkRepository;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

public class InstanceCustomLinkService {
  public static final String INSTANCE_CUSTOM_LINK_TABLE = "instance_custom_link";

  private final InstanceCustomLinkRepository repository;
  private final PostgresClient postgresClient;

  public InstanceCustomLinkService(Context context, Map<String, String> okapiHeaders) {
    this.repository = new InstanceCustomLinkRepository(context, okapiHeaders);
    this.postgresClient = PgUtil.postgresClient(context, okapiHeaders);
  }

  public Future<String> create(InstanceCustomLink entity) {
    return postgresClient.withTrans(conn -> repository.create(conn, entity));
  }
}
