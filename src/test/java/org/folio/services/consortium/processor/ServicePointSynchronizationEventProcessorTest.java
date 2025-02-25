package org.folio.services.consortium.processor;

import static org.folio.services.domainevent.DomainEvent.createEvent;
import static org.folio.services.domainevent.DomainEvent.deleteEvent;
import static org.folio.services.domainevent.DomainEvent.updateEvent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(VertxExtension.class)
class ServicePointSynchronizationEventProcessorTest {
  private static final String TENANT = "tenant";

  @Test
  void shouldFailToUpdateEventDueToProcessEventException(VertxTestContext testContext) {
    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(updateEvent(
      new Servicepoint(), new Servicepoint(), TENANT));
    processEventToThrowException(updateEventProcessor, testContext);
  }

  @Test
  void shouldFailToCreateEventDueToProcessEventException(VertxTestContext testContext) {
    var createEventProcessor = new ServicePointSynchronizationCreateEventProcessor(createEvent(
      new Servicepoint(), TENANT));
    processEventToThrowException(createEventProcessor, testContext);
  }

  @ParameterizedTest
  @MethodSource("servicePointProvider")
  void shouldReturnFalseIfServicePointsAreNull(Servicepoint oldServicepoint, Servicepoint newServicepoint) {
    String tenant = "tenant";
    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(oldServicepoint, newServicepoint, tenant));

    assertFalse(updateEventProcessor.validateEventEntity());
  }

  static Stream<Arguments> servicePointProvider() {
    return Stream.of(
      Arguments.of(null, null),
      Arguments.of(null, new Servicepoint()),
      Arguments.of(new Servicepoint(), null));
  }

  @Test
  void shouldReturnFalseIfServicePointIsNull() {
    var createEventProcessor = new ServicePointSynchronizationCreateEventProcessor(
      createEvent(null, TENANT));
    var deleteEventProcessor = new ServicePointSynchronizationDeleteEventProcessor(
      deleteEvent(null, TENANT));

    assertFalse(createEventProcessor.validateEventEntity());
    assertFalse(deleteEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnFalseIfServicePointsAreIdentical() {
    var servicepoint = new Servicepoint();
    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(servicepoint, servicepoint, TENANT));

    assertFalse(updateEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnTrueForUpdateIfNewServicePointIsValid() {
    var oldServicepoint = new Servicepoint().withId(UUID.randomUUID().toString());
    var newServicepoint = new Servicepoint().withId(UUID.randomUUID().toString());

    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(oldServicepoint, newServicepoint, TENANT));

    assertTrue(updateEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnTrueForCreateAndDeleteIfServicePointIsValid() {
    var servicepoint = new Servicepoint().withId(UUID.randomUUID().toString());
    var createEventProcessor = new ServicePointSynchronizationCreateEventProcessor(
      createEvent(servicepoint, TENANT));
    var deleteEventProcessor = new ServicePointSynchronizationDeleteEventProcessor(
      deleteEvent(servicepoint, TENANT));

    assertTrue(createEventProcessor.validateEventEntity());
    assertTrue(deleteEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnFalseIfValidationMessageIsNotNull() {
    Servicepoint oldServicepoint = new Servicepoint();
    Servicepoint newServicepoint = new Servicepoint()
      .withHoldShelfExpiryPeriod(new HoldShelfExpiryPeriod());

    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(oldServicepoint, newServicepoint, TENANT));
    var createEventProcessor = new ServicePointSynchronizationCreateEventProcessor(
      createEvent(newServicepoint, TENANT));

    assertFalse(updateEventProcessor.validateEventEntity());
    assertFalse(createEventProcessor.validateEventEntity());
  }

  private void processEventToThrowException(ServicePointSynchronizationEventProcessor processor,
                                            VertxTestContext testContext) {

    processor.processEvent(null, "")
      .onComplete(ar ->
        testContext.verify(() -> {
          assertInstanceOf(RuntimeException.class, ar.cause());
          testContext.completeNow();
        }));
  }
}
