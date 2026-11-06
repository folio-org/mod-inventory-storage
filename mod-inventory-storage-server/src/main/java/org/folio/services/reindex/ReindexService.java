package org.folio.services.reindex;

import static java.util.UUID.randomUUID;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.PENDING_CANCEL;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.model.ReindexJobs;
import org.folio.services.domainevent.DomainEventPublisherFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ReindexService {
  private final ReindexJobRepository reindexJobRepository;
  private final Function<Map<String, String>, ReindexJobRunner> runnerFactory;

  @Autowired
  public ReindexService(Context vertxContext, DomainEventPublisherFactory publisherFactory,
                        ReindexJobRepository reindexJobRepository) {
    this(reindexJobRepository,
      headers -> new ReindexJobRunner(vertxContext, headers, publisherFactory, reindexJobRepository));
  }

  ReindexService(ReindexJobRepository reindexJobRepository,
                 Function<Map<String, String>, ReindexJobRunner> runnerFactory) {
    this.reindexJobRepository = reindexJobRepository;
    this.runnerFactory = runnerFactory;
  }

  public Future<ReindexJobs> getJobs(String cql, int offset, int limit, String totalRecords,
                                     Map<String, String> headers) {
    return reindexJobRepository.query(cql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new ReindexJobs()
        .withReindexJobs(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<ReindexJob> getJob(String id, Map<String, String> headers) {
    return reindexJobRepository.findById(id, tenantId(headers));
  }

  public Future<ReindexJob> submitReindex(ReindexJob.ResourceName reindexResourceName,
                                          Map<String, String> headers) {
    var reindexResponse = buildInitialJob(reindexResourceName);

    return reindexJobRepository.save(reindexResponse.getId(), reindexResponse, tenantId(headers))
      .map(notUsed -> {
        runnerFactory.apply(headers).startReindex(reindexResponse);

        return reindexResponse;
      });
  }

  public Future<ReindexJob> cancelReindex(String jobId, Map<String, String> headers) {
    return reindexJobRepository.fetchAndUpdate(jobId,
        resp -> {
          if (resp.getJobStatus() == IDS_PUBLISHED) {
            throw new IllegalStateException("The job has been finished");
          }
          return resp.withJobStatus(PENDING_CANCEL);
        }, tenantId(headers))
      .onFailure(throwable -> {
        throw new BadRequestException(throwable.getMessage());
      });
  }

  private ReindexJob buildInitialJob(ReindexJob.ResourceName reindexResourceName) {
    return new ReindexJob()
      .withJobStatus(IN_PROGRESS)
      .withResourceName(reindexResourceName)
      .withPublished(0)
      .withSubmittedDate(new Date())
      .withId(randomUUID().toString());
  }
}
