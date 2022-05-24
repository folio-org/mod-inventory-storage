package org.folio.persist;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.cql.CQLWrapper;
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

  /**
   * Delete by CQL. For each deleted record return a {@link Row} with the instance id String
   * and with the instance jsonb String.
   *
   * <p>This automatically deletes connected marc records because the instance_source_marc foreign
   * key has "ON DELETE CASCADE".
   */
  public Future<RowSet<Row>> delete(String cql) {
    try {
      CQLWrapper cqlWrapper = new CQLWrapper(new CQL2PgJSON(tableName + ".jsonb"), cql, -1, -1);
      String sql = "DELETE FROM " + postgresClientFuturized.getFullTableName(tableName)
          + " " + cqlWrapper.getWhereClause()
          + " RETURNING id::text, jsonb::text";
      return postgresClient.execute(sql);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }
}
