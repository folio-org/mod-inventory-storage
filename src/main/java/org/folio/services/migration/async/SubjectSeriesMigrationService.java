package org.folio.services.migration.async;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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

  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE id in (%s) FOR UPDATE";
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
      .map(this::migrateSeriesAndSubjects)
      .map(json -> json.mapTo(Instance.class))
      .collect(Collectors.toList());
    return instanceRepository.updateBatch(instances, connection)
      .map(notUsed -> instances.size());
  }

  @Override
  public String getMigrationName() {
    return "subjectSeriesMigration";
  }

  private JsonObject migrateSeriesAndSubjects(JsonObject json) {
    migrateStringList(json, "subjects");
    migrateStringList(json, "series");
    return json;
  }

  private void migrateStringList(JsonObject json, String jsonArrayKey) {
    var stringArray = json.getJsonArray(jsonArrayKey);
    if (stringArray != null && !stringArray.isEmpty()) {
      var strings = stringArray.stream().map(Object::toString).collect(Collectors.toList());
      var jsonObjects = strings.stream()
        .map(s -> new JsonObject().put("value", s))
        .collect(Collectors.toList());
      var newObjectArray = new JsonArray(jsonObjects);
      json.put(jsonArrayKey, newObjectArray);
    }
  }

  private String selectSql() {
    String ids = getIdsForMigration().stream()
      .map(id -> "'" + id + "'")
      .collect(Collectors.joining(", "));
    return String.format(SELECT_SQL, postgresClient.getFullTableName("instance"), ids);
  }
}
