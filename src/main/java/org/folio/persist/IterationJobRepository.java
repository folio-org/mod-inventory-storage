package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.IterationJob;

public class IterationJobRepository extends AbstractRepository<IterationJob> {

  private static final String TABLE_NAME = "iteration_job";

  public IterationJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TABLE_NAME, IterationJob.class);
  }

  public Future<IterationJob> fetchAndUpdate(String id, UnaryOperator<IterationJob> builder) {
    return getById(id)
      .map(builder)
      .compose(response -> update(id, response)
        .map(response));
  }
}
