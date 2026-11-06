package org.folio.persist;

import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.COMPLETED;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.IterationJob;
import org.springframework.stereotype.Repository;

@Repository
public class IterationJobRepository extends AbstractRepository<IterationJob> {

  private static final String TABLE_NAME = "iteration_job";

  public IterationJobRepository(Context context) {
    super(context, TABLE_NAME, IterationJob.class);
  }

  public Future<IterationJob> fetchAndUpdateIterationJob(String id, UnaryOperator<IterationJob> builder,
                                                         String tenantId) {
    return inTransaction(tenantId, conn -> conn
      .getByIdForUpdate(TABLE_NAME, id, IterationJob.class)
      .compose(response -> {
        if (COMPLETED.equals(response.getJobStatus())) {
          return Future.succeededFuture(response);
        }
        var responseJob = builder.apply(response);
        return conn.update(TABLE_NAME, responseJob, id).map(responseJob);
      }));
  }
}
