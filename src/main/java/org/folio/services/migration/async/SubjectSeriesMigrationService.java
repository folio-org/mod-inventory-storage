package org.folio.services.migration.async;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;

public class SubjectSeriesMigrationService extends AsyncBaseMigrationService {

  private static final String SELECT_SQL = "SELECT migrate_series_and_subjects(jsonb) as jsonb FROM %s WHERE %s FOR UPDATE";
  private static final String WHERE_CONDITION = "id in (%s)";

  private final PostgresClientFuturized postgresClient;
  private final InstanceRepository instanceRepository;

  public SubjectSeriesMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new InstanceRepository(context, okapiHeaders));
  }

  public SubjectSeriesMigrationService(PostgresClientFuturized postgresClient,
                                       InstanceRepository instanceRepository) {

    super("26.0.0", postgresClient);
    this.postgresClient = postgresClient;
    this.instanceRepository = instanceRepository;
  }

  @Override
  protected Future<RowStream<Row>> openStream(SQLConnection connection) {
    return postgresClient.selectStream(connection, selectSql());
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch, SQLConnection connection) {
    var instances = batch.stream()
      .map(row -> row.getJsonObject("jsonb"))
      .map(json -> json.mapTo(Instance.class))
      .collect(Collectors.toList());
    return instanceRepository.updateBatch(instances, connection)
      .map(notUsed -> instances.size());
  }

  @Override
  public String getMigrationName() {
    return "subjectSeriesMigration";
  }

  private String selectSql() {
    var idsForMigration = getIdsForMigration();
    var whereCondition = "false";

    if (!idsForMigration.isEmpty()) {
      var ids = idsForMigration.stream()
        .map(id -> "'" + id + "'")
        .collect(Collectors.joining(", "));

      whereCondition = String.format(WHERE_CONDITION, ids);
    }

    return String.format(SELECT_SQL, postgresClient.getFullTableName("instance"), whereCondition);
  }
}
