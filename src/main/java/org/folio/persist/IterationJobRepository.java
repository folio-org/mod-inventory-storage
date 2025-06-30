package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.IterationJob;

public class IterationJobRepository extends AbstractRepository<IterationJob> {

  private static final String TABLE_NAME = "iteration_job";

  public IterationJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TABLE_NAME, IterationJob.class);
  }
}
