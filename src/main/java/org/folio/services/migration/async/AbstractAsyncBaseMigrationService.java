package org.folio.services.migration.async;

import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.List;
import java.util.stream.Collectors;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.utils.DatabaseUtils;

public abstract class AbstractAsyncBaseMigrationService extends AsyncBaseMigrationService {

  private static final String WHERE_CONDITION = "id in (%s)";

  protected final PostgresClient postgresClient;
  protected final InstanceRepository instanceRepository;

  protected AbstractAsyncBaseMigrationService(String version, PostgresClient postgresClient,
                                              InstanceRepository instanceRepository) {
    super(version, postgresClient);
    this.postgresClient = postgresClient;
    this.instanceRepository = instanceRepository;
  }

  @Override
  protected Future<RowStream<Row>> openStream(Conn connection) {
    return DatabaseUtils.selectStream(connection, selectSql());
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch, Conn connection) {
    var instances = batch.stream()
      .map(row -> row.getJsonObject("jsonb"))
      .map(json -> json.mapTo(Instance.class))
      .toList();
    return instanceRepository.upsertBatch(instances, connection)
      .map(notUsed -> instances.size());
  }

  protected abstract String getSelectSqlQuery();

  private String selectSql() {
    var idsForMigration = getIdsForMigration();
    var whereCondition = "false";

    if (!idsForMigration.isEmpty()) {
      var ids = idsForMigration.stream()
        .map(id -> String.format("'%s'", id))
        .collect(Collectors.joining(", "));

      whereCondition = String.format(WHERE_CONDITION, ids);
    }
    return String.format(getSelectSqlQuery(), postgresClient.getSchemaName() + '.' + INSTANCE_TABLE, whereCondition);
  }
}
