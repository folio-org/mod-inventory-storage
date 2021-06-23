package org.folio.services.migration.instance;

import static java.util.stream.Collectors.toList;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.List;
import java.util.Map;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.services.instance.InstanceEffectiveValuesService;
import org.folio.services.migration.BaseMigrationService;

public class PublicationPeriodMigrationService extends BaseMigrationService {
  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
    + "jsonb->>'publicationPeriod' IS NULL";

  private final PostgresClientFuturized postgresClient;
  private final InstanceRepository instanceRepository;
  private final InstanceEffectiveValuesService effectiveValuesService;

  public PublicationPeriodMigrationService(Context context, Map<String, String> headers) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, headers)),
      new InstanceRepository(context, headers));
  }

  private PublicationPeriodMigrationService(PostgresClientFuturized client,
    InstanceRepository instanceRepository) {

    super("20.3.0", client);
    this.postgresClient = client;
    this.instanceRepository = instanceRepository;
    this.effectiveValuesService = new InstanceEffectiveValuesService();
  }

  @Override
  protected Future<RowStream<Row>> openStream(SQLConnection connection) {
    return postgresClient.selectStream(connection, selectSql());
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch) {
    var instances = batch.stream()
      .map(row -> rowToClass(row, Instance.class))
      .map(effectiveValuesService::populateEffectiveValues)
      .collect(toList());

    return instanceRepository.update(instances).map(notUsed -> instances.size());
  }

  private String selectSql() {
    return String.format(SELECT_SQL, postgresClient.getFullTableName("instance"));
  }
}
