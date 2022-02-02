package org.folio.services.migration.async;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.services.instance.InstanceEffectiveValuesService;
import org.folio.services.migration.BaseMigrationService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PublicationPeriodMigrationService extends BaseMigrationService {
  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
    + "id in (%s)";
  private final PostgresClientFuturized postgresClient;
  private final InstanceRepository instanceRepository;
  private final AsyncMigrationJobRepository asyncMigrationJobRepository;
  private final InstanceEffectiveValuesService valuesService = new InstanceEffectiveValuesService();

  public PublicationPeriodMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new InstanceRepository(context, okapiHeaders),
      new AsyncMigrationJobRepository(context, okapiHeaders));
  }

  public PublicationPeriodMigrationService(PostgresClientFuturized postgresClient,
                                           InstanceRepository instanceRepository,
                                           AsyncMigrationJobRepository asyncMigrationJobRepository) {

    super("20.2.1", postgresClient);
    this.postgresClient = postgresClient;
    this.instanceRepository = instanceRepository;
    this.asyncMigrationJobRepository = asyncMigrationJobRepository;
  }

  @Override
  protected Future<RowStream<Row>> openStream(SQLConnection connection) {
    return postgresClient.selectStream(connection, selectSql());
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch) {
    var instances = batch.stream()
      .map(row -> rowToClass(row, Instance.class))
      .peek(valuesService::populatePublicationPeriod)
      .collect(Collectors.toList());
    return instanceRepository.update(instances).map(notUsed -> instances.size());
  }

  private String selectSql() {
    String ids = getIdsForMigration().stream()
      .map(id -> "'" + id + "'")
      .collect(Collectors.joining(", "));
    return String.format(SELECT_SQL, postgresClient.getFullTableName("instance"), ids);
  }

  public String getMigrationName() {
    return "publicationPeriodMigration";
  }
}
