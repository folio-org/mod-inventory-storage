package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.persist.PostgresClient;

public class ReindexJobRepository extends AbstractRepository<ReindexJob> {
  public static final String TABLE_NAME = "reindex_job";
  public static final String INSTANCE_REINDEX_JOBS_QUERY =
    "resourceName==Instance";

  public ReindexJobRepository(PostgresClient postgresClient) {
    super(postgresClient, TABLE_NAME, ReindexJob.class);
  }

  public ReindexJobRepository(Context context, Map<String, String> okapiHeaders) {
    this(postgresClient(context, okapiHeaders));
  }
}
