package org.folio.services.reindex;

import static java.util.UUID.randomUUID;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.PENDING_CANCEL;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Date;
import java.util.Map;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.ReindexJob;

public final class ReindexService {
  private final ReindexJobRepository reindexJobRepository;
  private final ReindexJobRunner jobRunner;

  public ReindexService(Context vertxContext, Map<String, String> okapiHeaders) {
    this(new ReindexJobRepository(vertxContext, okapiHeaders),
      new ReindexJobRunner(vertxContext, okapiHeaders));
  }

  public ReindexService(ReindexJobRepository repository, ReindexJobRunner runner) {
    this.reindexJobRepository = repository;
    this.jobRunner = runner;
  }

  public Future<ReindexJob> submitReindex(ReindexResourceName reindexResourceName) {
    var reindexResponse = buildInitialJob();

    return reindexJobRepository.save(reindexResponse.getId(), reindexResponse)
      .map(notUsed -> {
        jobRunner.startReindex(reindexResponse, reindexResourceName);

        return reindexResponse;
      });
  }

  public Future<ReindexJob> cancelReindex(String jobId) {
    return reindexJobRepository.fetchAndUpdate(jobId,
      resp -> resp.withJobStatus(PENDING_CANCEL));
  }

  private ReindexJob buildInitialJob() {
    return new ReindexJob()
      .withJobStatus(IN_PROGRESS)
      .withPublished(0)
      .withSubmittedDate(new Date())
      .withId(randomUUID().toString());
  }
}
