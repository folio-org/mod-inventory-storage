package org.folio.services.migration.instance;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
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

public class InstancePublicationPeriodMigrationService extends BaseMigrationService {
  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
    + "jsonb->>'publicationPeriod' IS NULL";

  private final PostgresClientFuturized postgresClient;
  private final InstanceRepository instanceRepository;

  public InstancePublicationPeriodMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new InstanceRepository(context, okapiHeaders));
  }

  public InstancePublicationPeriodMigrationService(
    PostgresClientFuturized postgresClient, InstanceRepository instanceRepository) {

    super("22.1.0", postgresClient);
    this.postgresClient = postgresClient;
    this.instanceRepository = instanceRepository;
  }

  @Override
  protected Future<RowStream<Row>> openStream(SQLConnection connection) {
    return postgresClient.selectStream(connection, selectSql());
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch) {
    var instances = batch.stream()
      .map(row -> rowToClass(row, Instance.class))
      .map(new InstanceEffectiveValuesService()::populateEffectiveValues)
      .collect(Collectors.toList());

    return instanceRepository.update(instances).map(notUsed -> instances.size());
  }

  private String selectSql() {
    return String.format(SELECT_SQL, postgresClient.getFullTableName("instance"));
  }
}
