package org.folio.services.reindex;

import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReindexServiceTest {

  private @Mock ReindexJobRunner runner;
  private @Mock ReindexJobRepository reindexJobRepository;

  private ReindexService reindexService;

  @BeforeEach
  void setUp() {
    reindexService = new ReindexService(reindexJobRepository, headers -> runner);
  }

  @Test
  void canSubmitReindex() {
    when(reindexJobRepository.save(any(), any(), any()))
      .thenReturn(Future.succeededFuture(UUID.randomUUID().toString()));

    var reindexJob = await(reindexService.submitReindex(ReindexJob.ResourceName.INSTANCE, Map.of()));

    assertThat(reindexJob.getJobStatus(), is(IN_PROGRESS));
    assertNotNull(reindexJob.getId());
    assertThat(reindexJob.getPublished(), is(0));
    assertNotNull(reindexJob.getSubmittedDate());

    verify(runner, times(1)).startReindex(any());
  }

  @Test
  void cannotCancelFinishedJob() {
    var jobId = UUID.randomUUID().toString();
    var reindexJob = new ReindexJob();
    reindexJob.withId(jobId);
    reindexJob.withJobStatus(IDS_PUBLISHED);
    when(reindexJobRepository.fetchAndUpdate(eq(jobId), any(), any()))
      .thenAnswer(invocation -> {
        UnaryOperator<ReindexJob> updater = invocation.getArgument(1);
        return Future.succeededFuture(updater.apply(reindexJob));
      });

    var headers = Map.<String, String>of();
    assertThrows(IllegalStateException.class,
      () -> reindexService.cancelReindex(jobId, headers));
  }
}
