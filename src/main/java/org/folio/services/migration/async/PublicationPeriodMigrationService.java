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
import org.folio.services.instance.InstanceEffectiveValuesService;

public class PublicationPeriodMigrationService extends AsyncBaseMigrationService {
  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
    + "id in (%s) FOR UPDATE";
  private final PostgresClientFuturized postgresClient;
  private final InstanceRepository instanceRepository;
  private final InstanceEffectiveValuesService valuesService = new InstanceEffectiveValuesService();

  public PublicationPeriodMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new InstanceRepository(context, okapiHeaders));
  }

  public PublicationPeriodMigrationService(PostgresClientFuturized postgresClient,
                                           InstanceRepository instanceRepository) {

    super("23.0.0", postgresClient);
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
      .map(row -> rowToClass(row, Instance.class))
      .peek(valuesService::populatePublicationPeriod)
      .collect(Collectors.toList());
    return instanceRepository.updateBatch(instances, connection)
      .map(notUsed -> instances.size());
  }

  public String getMigrationName() {
    return "publicationPeriodMigration";
  }

  private String selectSql() {
    String ids = getIdsForMigration().stream()
      .map(id -> "'" + id + "'")
      .collect(Collectors.joining(", "));
    return String.format(SELECT_SQL, postgresClient.getFullTableName("instance"), ids);
  }
}
