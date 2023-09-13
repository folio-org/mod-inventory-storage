package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.ReindexJob;

public class ReindexJobRepository extends AbstractRepository<ReindexJob> {
  public static final String TABLE_NAME = "reindex_job";
  public static final String INSTANCE_REINDEX_JOBS_QUERY =
    "resourceName==Instance";

  public ReindexJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TABLE_NAME, ReindexJob.class);
  }

  public Future<ReindexJob> fetchAndUpdate(String id, UnaryOperator<ReindexJob> builder) {
    return getById(id)
      .map(builder)
      .compose(response -> update(id, response)
        .map(response));
  }
}
