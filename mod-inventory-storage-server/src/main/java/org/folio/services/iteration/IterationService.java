package org.folio.services.iteration;

import static java.util.UUID.randomUUID;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.CANCELLATION_PENDING;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.folio.persist.InstanceRepository;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.folio.services.domainevent.DomainEventPublisherFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class IterationService {

  private final IterationJobRepository iterationJobRepository;
  private final Function<Map<String, String>, IterationJobRunner> runnerFactory;

  @Autowired
  public IterationService(Context vertxContext, DomainEventPublisherFactory publisherFactory,
                          IterationJobRepository iterationJobRepository, InstanceRepository instanceRepository) {
    this(iterationJobRepository, headers -> new IterationJobRunner(vertxContext, headers, publisherFactory,
      iterationJobRepository, instanceRepository));
  }

  IterationService(IterationJobRepository iterationJobRepository,
                   Function<Map<String, String>, IterationJobRunner> runnerFactory) {
    this.iterationJobRepository = iterationJobRepository;
    this.runnerFactory = runnerFactory;
  }

  public Future<Optional<IterationJob>> getIteration(String jobId, Map<String, String> headers) {
    return iterationJobRepository.getById(jobId, tenantId(headers)).map(Optional::ofNullable);
  }

  public Future<IterationJob> submitIteration(IterationJobParams jobParams,
                                                 Map<String, String> headers) {
    var job = buildInitialJob(jobParams);

    return iterationJobRepository.save(job.getId(), job, tenantId(headers))
      .map(notUsed -> {
        runnerFactory.apply(headers).startIteration(job);

        return job;
      });
  }

  public Future<Void> cancelIteration(String jobId, Map<String, String> headers) {
    return iterationJobRepository.fetchAndUpdate(jobId,
      resp -> resp.withJobStatus(CANCELLATION_PENDING), tenantId(headers)).mapEmpty();
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
