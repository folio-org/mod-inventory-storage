package org.folio.persist;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.COMPLETED;
import static org.folio.rest.jaxrs.model.IterationJob.JobStatus.IN_PROGRESS;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.rest.api.TestBase;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class IterationJobRepositoryTest extends TestBase {

  private static final String JOB_ID = "test-job-id";

  private PostgresClient postgresClient;
  private IterationJobRepository repository;
  private Conn conn;

  @Before
  public void setUp() {
    postgresClient = mock(PostgresClient.class);
    conn = mock(Conn.class);
    try (var pgUtilMock = mockStatic(PgUtil.class)) {
      pgUtilMock.when(() -> PgUtil.postgresClient(any(), any())).thenReturn(postgresClient);
      repository = spy(new IterationJobRepository(getContext(), okapiHeaders()));
    }
  }

  @Test
  public void fetchAndUpdateIterationJob_ShouldReturnJob_WhenStatusIsCompleted() {
    // Arrange
    when(conn.getByIdForUpdate(anyString(), anyString(), eq(IterationJob.class)))
      .thenReturn(Future.succeededFuture(new IterationJob().withId(JOB_ID).withJobStatus(COMPLETED)));
    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> {
        Function<Conn, Future<IterationJob>> function = invocation.getArgument(0);
        return function.apply(conn);
      });

    // Act
    var result = repository.fetchAndUpdateIterationJob(JOB_ID, job -> job);

    // Assert
    assertTrue(result.succeeded());
    assertEquals(COMPLETED, result.result().getJobStatus());
    verify(conn, never()).update(anyString(), any(), anyString());
  }

  @Test
  public void fetchAndUpdateIterationJob_ShouldUpdateJob_WhenStatusIsNotCompleted() {
    // Arrange
    var updatedJob = new IterationJob().withId(JOB_ID)
      .withJobStatus(IN_PROGRESS)
      .withMessagesPublished(100);
    when(conn.getByIdForUpdate(anyString(), anyString(), eq(IterationJob.class)))
      .thenReturn(Future.succeededFuture(updatedJob));
    when(conn.update(anyString(), any(), anyString()))
      .thenReturn(Future.succeededFuture(null));
    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> {
        Function<Conn, Future<IterationJob>> function = invocation.getArgument(0);
        return function.apply(conn);
      });

    // Act
    var result = repository.fetchAndUpdateIterationJob(JOB_ID, job -> job);

    // Assert
    assertTrue(result.succeeded());
    assertEquals(IN_PROGRESS, result.result().getJobStatus());
    assertEquals(100, result.result().getMessagesPublished());
    verify(conn).update(anyString(), eq(updatedJob), eq(JOB_ID));
  }

  @Test
  public void fetchAndUpdateIterationJob_ShouldFail_WhenFetchFails() {
    // Arrange
    when(conn.getByIdForUpdate(anyString(), anyString(), eq(IterationJob.class)))
      .thenReturn(Future.failedFuture(new RuntimeException("Fetch failed")));
    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> {
        Function<Conn, Future<IterationJob>> function = invocation.getArgument(0);
        return function.apply(conn);
      });

    // Act
    var result = repository.fetchAndUpdateIterationJob(JOB_ID, job -> job);

    // Assert
    assertTrue(result.failed());
    assertEquals("Fetch failed", result.cause().getMessage());
  }

  @Test
  public void fetchAndUpdateIterationJob_ShouldFail_WhenUpdateFails() {
    // Arrange
    when(conn.getByIdForUpdate(anyString(), anyString(), eq(IterationJob.class)))
      .thenReturn(Future.succeededFuture(new IterationJob().withId(JOB_ID).withJobStatus(IN_PROGRESS)));
    when(conn.update(anyString(), any(), anyString()))
      .thenReturn(Future.failedFuture(new RuntimeException("Update failed")));
    when(postgresClient.withTrans(any()))
      .thenAnswer(invocation -> {
        Function<Conn, Future<IterationJob>> function = invocation.getArgument(0);
        return function.apply(conn);
      });

    // Act
    var result = repository.fetchAndUpdateIterationJob(JOB_ID, job -> job);

    // Assert
    assertTrue(result.failed());
    assertEquals("Update failed", result.cause().getMessage());
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(TENANT.toLowerCase(), TENANT_ID));
  }

  private static Context getContext() {
    return getVertx().getOrCreateContext();
  }
}
