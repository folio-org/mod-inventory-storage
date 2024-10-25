package org.folio.services.migration.async;

import static org.folio.services.migration.MigrationName.PUBLICATION_PERIOD_MIGRATION;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.persist.InstanceRepository;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;

public class PublicationPeriodMigrationService extends AbstractAsyncBaseMigrationService {

  private static final String SELECT_SQL = """
     SELECT migrate_publication_period(jsonb) as jsonb
     FROM %s
     WHERE %s FOR UPDATE
    """;

  public PublicationPeriodMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new InstanceRepository(context, okapiHeaders));
  }

  public PublicationPeriodMigrationService(PostgresClientFuturized postgresClient,
                                                    InstanceRepository instanceRepository) {

    super("28.0.0", postgresClient, instanceRepository);
  }

  @Override
  public String getMigrationName() {
    return PUBLICATION_PERIOD_MIGRATION.getValue();
  }

  @Override
  protected String getSelectSqlQuery() {
    return SELECT_SQL;
  }
}
