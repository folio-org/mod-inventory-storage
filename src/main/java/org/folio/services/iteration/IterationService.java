package org.folio.services.iteration;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Date;
import java.util.Map;
import static java.util.UUID.randomUUID;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.folio.rest.jaxrs.model.ReindexJob;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.PENDING_CANCEL;
import org.folio.services.reindex.ReindexJobRunner;

public final class IterationService {

  private final ReindexJobRepository reindexJobRepository;
  private final ReindexJobRunner jobRunner;

  public IterationService(Context vertxContext, Map<String, String> okapiHeaders) {
    this(new ReindexJobRepository(vertxContext, okapiHeaders),
      new ReindexJobRunner(vertxContext, okapiHeaders));
  }

  public IterationService(ReindexJobRepository repository, ReindexJobRunner runner) {
    this.reindexJobRepository = repository;
    this.jobRunner = runner;
  }

  public Future<IterationJob> submitIteration(IterationJobParams jobParams) {
    var iterationJob = buildInitialJob();

    /*return reindexJobRepository.save(reindexResponse.getId(), reindexResponse)
      .map(notUsed -> {
        jobRunner.startReindex(reindexResponse);

        return reindexResponse;
      });*/
    return Future.succeededFuture(iterationJob);
  }

  public Future<ReindexJob> cancelIteration(String jobId) {
    return reindexJobRepository.fetchAndUpdate(jobId,
      resp -> resp.withJobStatus(PENDING_CANCEL));
  }

  private IterationJob buildInitialJob() {
    return new IterationJob()
      .withJobStatus(IterationJob.JobStatus.IN_PROGRESS)
      .withPublished(0)
      .withSubmittedDate(new Date())
      .withId(randomUUID().toString());
  }
}
