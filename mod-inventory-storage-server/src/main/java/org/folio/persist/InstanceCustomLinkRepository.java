package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.instance.InstanceCustomLinkService.INSTANCE_CUSTOM_LINK_TABLE;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.persist.Conn;

public class InstanceCustomLinkRepository extends AbstractRepository<InstanceCustomLink> {
  private static final int MAXIMUM_LINK_COUNT = 10;

  public InstanceCustomLinkRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_CUSTOM_LINK_TABLE, InstanceCustomLink.class);
  }

  public Future<String> create(Conn conn, InstanceCustomLink entity) {
    return conn
      .execute(String.format("LOCK TABLE %s IN EXCLUSIVE MODE", INSTANCE_CUSTOM_LINK_TABLE))
      .compose(x -> conn.execute(String.format("SELECT COUNT(*) AS linkcount FROM %s", INSTANCE_CUSTOM_LINK_TABLE)))
      .compose(rowSet -> {
        int linkCount = rowSet.iterator().next().getInteger("linkcount");
        if (linkCount >= MAXIMUM_LINK_COUNT) {
          var ve = new ValidationException(new Errors().withErrors(List.of(
            new Error().withCode("maximumCount")
              .withMessage(String.format("Maximum limit of %d instance custom links reached", MAXIMUM_LINK_COUNT)))));
          return Future.failedFuture(ve);
        }
        return conn.save(INSTANCE_CUSTOM_LINK_TABLE, entity);
      });
  }
}
