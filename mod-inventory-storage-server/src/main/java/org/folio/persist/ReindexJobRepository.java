package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.springframework.stereotype.Repository;

@Repository
public class ReindexJobRepository extends AbstractRepository<ReindexJob> {

  public static final String TABLE_NAME = "reindex_job";
  public static final String INSTANCE_REINDEX_JOBS_QUERY = "resourceName==Instance";

  public ReindexJobRepository(Context context) {
    super(context, TABLE_NAME, ReindexJob.class);
  }
}
