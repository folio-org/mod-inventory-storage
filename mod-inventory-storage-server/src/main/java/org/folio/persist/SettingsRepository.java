package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Setting;

public class SettingsRepository extends AbstractRepository<Setting> {

  private static final String SETTINGS_TABLE = "settings";

  public SettingsRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), SETTINGS_TABLE, Setting.class);
  }

  public Future<Setting> findByKey(String key) {
    var sql = "SELECT * FROM %s WHERE key = $1".formatted(getFullTableName(SETTINGS_TABLE));
    return postgresClient.execute(sql, Tuple.of(key))
      .map(rowSet -> {
        var iterator = rowSet.iterator();
        if (!iterator.hasNext()) {
          return null;
        }
        return mapToSetting(iterator.next());
      });
  }

  public Future<Setting> update(Setting setting) {
    var sql = "UPDATE %s SET value = $1, updated_date = now(), updated_by_user_id = $2 WHERE key = $3 RETURNING *"
      .formatted(getFullTableName(SETTINGS_TABLE));
    return postgresClient.execute(sql, Tuple.of(
        setting.getValue(),
        setting.getUpdatedByUserId(),
        setting.getKey()))
      .map(rows -> {
        return mapToSetting(rows.iterator().next());
      });
  }

  private Setting mapToSetting(Row row) {
    return new Setting()
      .withId(UUID.fromString(row.getUUID("id").toString()))
      .withKey(row.getString("key"))
      .withValue(row.getString("value"))
      .withType(Setting.Type.valueOf(row.getString("type")))
      .withCentralManaged(row.getBoolean("central_managed"))
      .withDescription(row.getString("description"));
  }
}
