package org.folio.services.migration.async;

import static org.folio.services.migration.MigrationName.SUBJECT_SERIES_MIGRATION;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.persist.InstanceRepository;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;

public class SubjectSeriesMigrationService extends AbstractAsyncBaseMigrationService {

  private static final String SELECT_SQL =
    "SELECT migrate_series_and_subjects(jsonb) as jsonb FROM %s WHERE %s FOR UPDATE";

  public SubjectSeriesMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new InstanceRepository(context, okapiHeaders));
  }

  public SubjectSeriesMigrationService(PostgresClientFuturized postgresClient,
                                       InstanceRepository instanceRepository) {

    super("26.0.0", postgresClient, instanceRepository);
  }

  @Override
  public String getMigrationName() {
    return SUBJECT_SERIES_MIGRATION.getValue();
  }

  @Override
  protected String getSelectSqlQuery() {
    return SELECT_SQL;
  }
}
