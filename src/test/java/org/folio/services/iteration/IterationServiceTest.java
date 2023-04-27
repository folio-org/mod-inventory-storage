package org.folio.services.iteration;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
import org.junit.Before;
import org.junit.Test;

public class IterationServiceTest {

  private IterationJobRepository repository;
  private IterationJobRunner runner;
  private IterationService service;

  @Before
  public void setUp() {
    repository = mock(IterationJobRepository.class);
    runner = mock(IterationJobRunner.class);
    service = new IterationService(repository, runner);
  }

  @Test
  public void canSubmitIteration() {
    when(repository.save(any(), any()))
      .thenReturn(Future.succeededFuture(UUID.randomUUID().toString()));

    IterationJobParams jobParams = new IterationJobParams()
      .withEventType("ITERATE")
      .withTopicName("inventory.instance.iteration");

    var job = get(service.submitIteration(jobParams));

    assertThat(job.getId(), notNullValue());
    assertThat(job.getJobStatus(), is(IN_PROGRESS));
    assertThat(job.getMessagesPublished(), is(0));
    assertThat(job.getSubmittedDate(), notNullValue());
    assertThat(job.getJobParams(), is(jobParams));

    verify(runner, times(1)).startIteration(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void canCancelIteration() {
    var jobId = UUID.randomUUID().toString();

    when(repository.fetchAndUpdate(eq(jobId), isA(UnaryOperator.class)))
      .thenReturn(Future.succeededFuture());

    var result = get(service.cancelIteration(jobId));

    assertThat(result, nullValue());
  }

  @Test
  public void canGetIteration() {
    var jobId = UUID.randomUUID().toString();
    IterationJob existing = new IterationJob().withId(jobId);

    when(repository.getById(jobId))
      .thenReturn(Future.succeededFuture(existing));

    var job = get(service.getIteration(jobId));

    assertThat(job.isPresent(), is(true));
    assertThat(job.get(), is(existing));
  }

  @Test
  public void canGetEmptyIteration() {
    var jobId = UUID.randomUUID().toString();

    when(repository.getById(jobId))
      .thenReturn(Future.succeededFuture(null));

    var job = get(service.getIteration(jobId));

    assertThat(job.isPresent(), is(false));
  }

}
