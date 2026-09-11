package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.persist.AbstractRepository;
import org.folio.services.batch.BatchOperationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractDomainEventPublisherTest {

  private static final int CREATED = 201;
  private static final int NO_CONTENT = 204;
  private static final int BAD_REQUEST = 400;

  @SuppressWarnings("unchecked")
  private final AbstractRepository<String> repository = mock(AbstractRepository.class);
  private final CommonDomainEventPublisher<TestEvent> domainEventService = mock(CommonDomainEventPublisher.class);
  private final TestPublisher publisher = new TestPublisher(repository, domainEventService);

  @Test
  @DisplayName("should skip publishing when the create response is not a success")
  void shouldSkipPublishing_whenCreateResponseIsNotSuccess() {
    publisher.publishCreated().handle(responseWithStatus(BAD_REQUEST));

    verifyNoInteractions(domainEventService);
  }

  @Test
  @DisplayName("should publish the created record when the create response is a success")
  void shouldPublishCreatedRecord_whenCreateResponseIsSuccess() {
    when(domainEventService.publishRecordsCreated(any())).thenReturn(succeededFuture());
    var response = responseWithStatus(CREATED);
    when(response.getEntity()).thenReturn("record-1");

    publisher.publishCreated().handle(response);

    verify(domainEventService).publishRecordsCreated(List.of(Pair.of("record-1", event("record-1"))));
  }

  @Test
  @DisplayName("should skip publishing when the create-or-update response is not a success")
  void shouldSkipPublishing_whenCreateOrUpdateResponseIsNotSuccess() {
    var batch = new BatchOperationContext<>(List.of("new-1"), List.of("old-1"), true);

    publisher.publishCreatedOrUpdated(batch).handle(responseWithStatus(BAD_REQUEST));

    verifyNoInteractions(domainEventService);
  }

  @Test
  @DisplayName("should skip publishing when the batch operation disables events")
  void shouldSkipPublishing_whenBatchOperationDisablesEvents() {
    var batch = new BatchOperationContext<>(List.of("new-1"), List.of("old-1"), false);

    publisher.publishCreatedOrUpdated(batch).handle(responseWithStatus(CREATED));

    verifyNoInteractions(domainEventService);
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("should publish created and updated records when the batch operation enables events")
  void shouldPublishCreatedAndUpdated_whenBatchOperationEnablesEvents() {
    when(domainEventService.publishRecordsCreated(any())).thenReturn(succeededFuture());
    when(repository.getByIds(anyCollection(), ArgumentMatcherHelper.<String>anyFunction()))
      .thenReturn(succeededFuture(Map.of("old-1", "old-1")));
    when(domainEventService.publishRecordsUpdated(any())).thenReturn(succeededFuture());
    var batch = new BatchOperationContext<>(List.of("new-1"), List.of("old-1"), true);

    publisher.publishCreatedOrUpdated(batch).handle(responseWithStatus(CREATED));

    verify(domainEventService).publishRecordsCreated(List.of(Pair.of("new-1", event("new-1"))));
    verify(domainEventService).publishRecordsUpdated(
      List.of(Triple.of("old-1", event("old-1"), event("old-1"))));
  }

  @Test
  @DisplayName("should skip publishing when the remove response is not a success")
  void shouldSkipPublishing_whenRemoveResponseIsNotSuccess() {
    publisher.publishRemoved("record-1").handle(responseWithStatus(BAD_REQUEST));

    verifyNoInteractions(domainEventService);
  }

  @Test
  @DisplayName("should publish the removed record when the remove response is a success")
  void shouldPublishRemovedRecord_whenRemoveResponseIsSuccess() {
    when(domainEventService.publishRecordRemoved(eq("record-1"), any(TestEvent.class)))
      .thenReturn(succeededFuture());

    publisher.publishRemoved("record-1").handle(responseWithStatus(NO_CONTENT));

    verify(domainEventService).publishRecordRemoved("record-1", event("record-1"));
  }

  @Test
  @DisplayName("should delegate to the domain event service when removing a raw record")
  void shouldDelegateToDomainEventService_whenRemovingRawRecord() {
    publisher.publishRemoved("instance-1", "raw-record");

    verify(domainEventService).publishRecordRemoved("instance-1", "raw-record");
  }

  @Test
  @DisplayName("should delegate to the domain event service when removing all records")
  void shouldDelegateToDomainEventService_whenRemovingAllRecords() {
    when(domainEventService.publishAllRecordsRemoved()).thenReturn(succeededFuture());

    publisher.publishAllRemoved();

    verify(domainEventService).publishAllRecordsRemoved();
  }

  @Test
  @DisplayName("should skip publishing when the update response is not a success")
  void shouldSkipPublishing_whenUpdateResponseIsNotSuccess() {
    publisher.publishUpdated("old-1").handle(responseWithStatus(BAD_REQUEST));

    verifyNoInteractions(domainEventService);
  }

  @Test
  @DisplayName("should publish the updated record when the update response is a success")
  void shouldPublishUpdatedRecord_whenUpdateResponseIsSuccess() {
    when(repository.getByIds(anyCollection(), ArgumentMatcherHelper.<String>anyFunction()))
      .thenReturn(succeededFuture(Map.of("old-1", "old-1")));
    when(domainEventService.publishRecordsUpdated(any())).thenReturn(succeededFuture());

    publisher.publishUpdated("old-1").handle(responseWithStatus(NO_CONTENT));

    verify(domainEventService).publishRecordsUpdated(
      List.of(Triple.of("old-1", event("old-1"), event("old-1"))));
  }

  @Test
  @DisplayName("should skip publishing when the batch update response is not a success")
  void shouldSkipPublishing_whenBatchUpdateResponseIsNotSuccess() {
    var batch = new BatchOperationContext<>(List.<String>of(), List.of("old-1"), true);

    publisher.publishUpdated(batch).handle(responseWithStatus(BAD_REQUEST));

    verifyNoInteractions(domainEventService);
  }

  @Test
  @DisplayName("should skip publishing when the batch update disables events")
  void shouldSkipPublishing_whenBatchUpdateDisablesEvents() {
    var batch = new BatchOperationContext<>(List.<String>of(), List.of("old-1"), false);

    publisher.publishUpdated(batch).handle(responseWithStatus(NO_CONTENT));

    verifyNoInteractions(domainEventService);
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("should skip publishing when there are no records to update")
  void shouldSkipPublishing_whenNoRecordsToUpdate() {
    var result = publisher.callPublishUpdated(List.of());

    assertThat(result.succeeded()).isTrue();
    verifyNoInteractions(repository);
    verifyNoInteractions(domainEventService);
  }

  @Test
  @DisplayName("should pair each new record with its matching old record by id")
  void shouldPairEachNewRecordWithMatchingOldRecord_byId() {
    var oldRecords = List.of(Pair.of("old-1", "old-1"));
    var newRecords = List.of(Pair.of("new-1", "old-1"));

    var result = publisher.callMapOldRecordsToNew(oldRecords, newRecords);

    assertThat(result).containsExactly(Triple.of("new-1", event("old-1"), event("old-1")));
  }

  private static TestEvent event(String domain) {
    return new TestEvent("event:" + domain);
  }

  private static Response responseWithStatus(int status) {
    var response = mock(Response.class);
    when(response.getStatus()).thenReturn(status);
    return response;
  }

  private record TestEvent(String value) { }

  /**
   * D = domain type is the record's own id string; E = TestEvent wraps "event:" + domain, kept
   * distinct from String so it can't collide with CommonDomainEventPublisher's raw-String
   * publishRecordRemoved overload.
   */
  private static final class TestPublisher extends AbstractDomainEventPublisher<String, TestEvent> {
    private TestPublisher(AbstractRepository<String> repository, CommonDomainEventPublisher<TestEvent> service) {
      super(repository, service);
    }

    @Override
    protected Future<List<Pair<String, String>>> getRecordIds(Collection<String> domainTypes) {
      return succeededFuture(domainTypes.stream().map(domain -> Pair.of(domain, domain)).toList());
    }

    @Override
    protected TestEvent convertDomainToEvent(String instanceId, String domain) {
      return event(domain);
    }

    @Override
    protected String getId(String entity) {
      return entity;
    }

    private Future<Void> callPublishUpdated(Collection<String> oldRecords) {
      return publishUpdated(oldRecords);
    }

    private List<Triple<String, TestEvent, TestEvent>> callMapOldRecordsToNew(
      List<Pair<String, String>> oldRecords, List<Pair<String, String>> newRecords) {
      return mapOldRecordsToNew(oldRecords, newRecords);
    }
  }

  /**
   * Mockito's own {@code any()} can't infer a {@code Function<String, String>} type witness
   * cleanly at the call site - this narrows it without an unchecked warning at each use.
   */
  private static final class ArgumentMatcherHelper {
    private ArgumentMatcherHelper() { }

    static <T> Function<T, String> anyFunction() {
      return any();
    }
  }
}
