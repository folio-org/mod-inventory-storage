package org.folio.persist;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.SQLConnection;

import java.util.Map;

import static org.folio.rest.persist.PgUtil.postgresClient;

public class InstanceRepository extends AbstractRepository<Instance> {
  public static final String INSTANCE_TABLE = "instance";

  public InstanceRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), INSTANCE_TABLE, Instance.class);
  }

  public Future<RowStream<Row>> getAllIds(SQLConnection connection) {
    return postgresClientFuturized.selectStream(connection,
      "SELECT id FROM " + postgresClientFuturized.getFullTableName(INSTANCE_TABLE));
  }
}
