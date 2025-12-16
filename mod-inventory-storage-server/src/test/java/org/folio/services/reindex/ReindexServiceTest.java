package org.folio.services.reindex;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IDS_PUBLISHED;
import static org.folio.rest.jaxrs.model.ReindexJob.JobStatus.IN_PROGRESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.UUID;
import java.util.function.Function;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.junit.Test;

public class ReindexServiceTest {

  private final ReindexJobRunner runner = mock(ReindexJobRunner.class);
  private final PostgresClient postgresClient = mock(PostgresClient.class);
  private final ReindexJobRepository repository = new ReindexJobRepository(postgresClient);
  private final ReindexService reindexService = new ReindexService(repository, runner);
  private final Conn connection = mock(Conn.class);

  @Test
  public void canSubmitReindex() {
    when(postgresClient.save(any(), any(), any(ReindexJob.class)))
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

    when(postgresClient.withTrans(any()))
      .thenAnswer(invocationOnMock -> {
        var function = invocationOnMock.<Function<Conn, Future<ReindexJob>>>getArgument(0);
        return function.apply(connection);
      });
    when(connection.getByIdForUpdate(any(), eq(reindexJob.getId()), eq(ReindexJob.class)))
      .thenReturn(Future.succeededFuture(reindexJob));

    get(reindexService.cancelReindex(reindexJob.getId()));
  }
}
