package org.folio.services.iteration;

import static java.util.UUID.randomUUID;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.CANCELLATION_PENDING;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;

public final class IterationService {

  private final IterationJobRepository repository;
  private final IterationJobRunner jobRunner;

  public IterationService(Context vertxContext, Map<String, String> okapiHeaders) {
    this(new IterationJobRepository(vertxContext, okapiHeaders),
      new IterationJobRunner(vertxContext, okapiHeaders));
  }

  public IterationService(IterationJobRepository repository, IterationJobRunner runner) {
    this.repository = repository;
    this.jobRunner = runner;
  }

  public Future<Optional<IterationJob>> getIteration(String jobId) {
    return repository.getById(jobId).map(Optional::ofNullable);
  }

  public Future<IterationJob> submitIteration(IterationJobParams jobParams) {
    var job = buildInitialJob(jobParams);

    return repository.save(job.getId(), job)
      .map(notUsed -> {
        jobRunner.startIteration(job);

        return job;
      });
  }

  public Future<Void> cancelIteration(String jobId) {
    return repository.fetchAndUpdate(jobId,
      resp -> resp.withJobStatus(CANCELLATION_PENDING)).mapEmpty();
  }

  private IterationJob buildInitialJob(IterationJobParams jobParams) {
    return new IterationJob()
      .withJobParams(jobParams)
      .withJobStatus(IN_PROGRESS)
      .withMessagesPublished(0)
      .withSubmittedDate(new Date())
      .withId(randomUUID().toString());
  }
}
