package org.folio.services.reindex;

import static java.util.UUID.randomUUID;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.CANCELLED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Date;
import java.util.Map;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.ReindexJob;

public final class ReindexService {
  private final ReindexJobRepository reindexJobRepository;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;

  public ReindexService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.reindexJobRepository = new ReindexJobRepository(vertxContext, okapiHeaders);
  }

  public Future<ReindexJob> submitReindex() {
    var reindexResponse = buildInitialJob();

    return reindexJobRepository.save(reindexResponse.getId(), reindexResponse)
      .map(notUsed -> {
        new ReindexJobRunner(vertxContext, okapiHeaders, reindexResponse)
          .startReindex();

        return reindexResponse;
      });
  }

  public Future<ReindexJob> cancelReindex(String jobId) {
    return reindexJobRepository.fetchAndUpdate(jobId,
      resp -> resp.withJobStatus(CANCELLED));
  }

  private ReindexJob buildInitialJob() {
    return new ReindexJob()
      .withJobStatus(IN_PROGRESS)
      .withPublished(0)
      .withSubmittedDate(new Date())
      .withId(randomUUID().toString());
  }
}
