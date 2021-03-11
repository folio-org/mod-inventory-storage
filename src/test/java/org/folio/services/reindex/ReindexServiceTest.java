package org.folio.services.reindex;

import static org.folio.rest.api.TestBase.get;
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
import org.junit.Test;

public class ReindexServiceTest {
  @Test
  public void canSubmitReindex() {
    var repository = mock(ReindexJobRepository.class);
    var runner = mock(ReindexJobRunner.class);
    var reindexService = new ReindexService(repository, runner);

    when(repository.save(any(), any()))
      .thenReturn(Future.succeededFuture(UUID.randomUUID().toString()));

    var reindexJob = get(reindexService.submitReindex());

    assertThat(reindexJob.getJobStatus(), is(IN_PROGRESS));
    assertThat(reindexJob.getId(), notNullValue());
    assertThat(reindexJob.getPublished(), is(0));
    assertThat(reindexJob.getSubmittedDate(), notNullValue());

    verify(runner, times(1)).startReindex(any());
  }
}
