package org.folio.persist;

import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.COMPLETED;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import java.util.function.UnaryOperator;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.IterationJob;

public class IterationJobRepository extends AbstractRepository<IterationJob> {

  private static final String TABLE_NAME = "iteration_job";

  public IterationJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TABLE_NAME, IterationJob.class);
  }

  public Future<IterationJob> fetchAndUpdateIterationJob(String id, UnaryOperator<IterationJob> builder) {
    return postgresClient.withTrans(conn -> conn.getByIdForUpdate(TABLE_NAME, id, IterationJob.class)
      .compose(response -> {
        if (COMPLETED.equals(response.getJobStatus())) {
          return Future.succeededFuture(response);
        }
        return conn.update(TABLE_NAME, builder.apply(response), id)
          .map(builder.apply(response));
      }));
  }
}
