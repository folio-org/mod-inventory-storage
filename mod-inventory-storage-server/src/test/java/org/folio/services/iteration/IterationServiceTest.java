package org.folio.services.iteration;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IterationServiceTest {

  private IterationJobRepository repository;
  private IterationJobRunner runner;
  private IterationService service;

  @BeforeEach
  void setUp() {
    repository = mock(IterationJobRepository.class);
    runner = mock(IterationJobRunner.class);
    service = new IterationService(repository, runner);
  }

  @Test
  void canSubmitIteration() {
    when(repository.save(any(), any()))
      .thenReturn(Future.succeededFuture(UUID.randomUUID().toString()));

    IterationJobParams jobParams = new IterationJobParams()
      .withEventType("ITERATE")
      .withTopicName("inventory.instance.iteration");

    var job = get(service.submitIteration(jobParams));

    assertNotNull(job.getId());
    assertThat(job.getJobStatus(), is(IN_PROGRESS));
    assertThat(job.getMessagesPublished(), is(0));
    assertNotNull(job.getSubmittedDate());
    assertThat(job.getJobParams(), is(jobParams));

    verify(runner, times(1)).startIteration(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void canCancelIteration() {
    var jobId = UUID.randomUUID().toString();

    when(repository.fetchAndUpdate(eq(jobId), isA(UnaryOperator.class)))
      .thenReturn(Future.succeededFuture());

    var result = get(service.cancelIteration(jobId));

    assertNull(result);
  }

  @Test
  void canGetIteration() {
    var jobId = UUID.randomUUID().toString();
    IterationJob existing = new IterationJob().withId(jobId);

    when(repository.getById(jobId))
      .thenReturn(Future.succeededFuture(existing));

    var job = get(service.getIteration(jobId));

    assertThat(job.isPresent(), is(true));
    assertThat(job.get(), is(existing));
  }

  @Test
  void canGetEmptyIteration() {
    var jobId = UUID.randomUUID().toString();

    when(repository.getById(jobId))
      .thenReturn(Future.succeededFuture(null));

    var job = get(service.getIteration(jobId));

    assertThat(job.isPresent(), is(false));
  }
}
