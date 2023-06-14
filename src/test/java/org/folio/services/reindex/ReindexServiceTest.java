package org.folio.services.reindex;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.UUID;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.junit.Test;

public class ReindexServiceTest {

  private final ReindexJobRunner runner = mock(ReindexJobRunner.class);
  private final ReindexJobRepository repository = mock(ReindexJobRepository.class);
  private final ReindexService reindexService = new ReindexService(repository, runner);

  @Test
  public void canSubmitReindex() {
    when(repository.save(any(), any()))
      .thenReturn(Future.succeededFuture(UUID.randomUUID().toString()));

    var reindexJob = get(reindexService.submitReindex(ReindexJob.ResourceName.INSTANCE));

    assertThat(reindexJob.getJobStatus(), is(IN_PROGRESS));
    assertThat(reindexJob.getId(), notNullValue());
    assertThat(reindexJob.getPublished(), is(0));
    assertThat(reindexJob.getSubmittedDate(), notNullValue());

    verify(runner, times(1)).startReindex(any());
  }

  @Test(expected = BadRequestException.class)
  public void cannotCancelFinishedJob() {
    var reindexJob = new ReindexJob();
    reindexJob.withId(UUID.randomUUID().toString());
    reindexJob.withJobStatus(IDS_PUBLISHED);

    when(repository.fetchAndUpdate(any(), any())).thenCallRealMethod();
    when(repository.getById(reindexJob.getId()))
      .thenReturn(Future.succeededFuture(reindexJob));

    get(reindexService.cancelReindex(reindexJob.getId()));
  }
}
