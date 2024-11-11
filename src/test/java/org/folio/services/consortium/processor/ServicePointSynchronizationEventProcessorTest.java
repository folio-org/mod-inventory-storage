package org.folio.services.consortium.processor;

import static org.folio.services.domainevent.DomainEvent.createEvent;
import static org.folio.services.domainevent.DomainEvent.deleteEvent;
import static org.folio.services.domainevent.DomainEvent.updateEvent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class ServicePointSynchronizationEventProcessorTest {
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

  @Test
  void shouldReturnFalseIfBothServicePointsAreNull() {
    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(null, null, TENANT));

    assertFalse(updateEventProcessor.validateEventEntity());
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
    Servicepoint servicepoint = new Servicepoint();
    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(servicepoint, servicepoint, TENANT));

    assertFalse(updateEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnTrueIfNewServicePointIsValid() {
    Servicepoint oldServicepoint = new Servicepoint().withId(UUID.randomUUID().toString());
    Servicepoint newServicepoint = new Servicepoint().withId(UUID.randomUUID().toString());

    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(oldServicepoint, newServicepoint, TENANT));

    assertTrue(updateEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnFalseIfValidationMessageIsNotNull() {
    Servicepoint oldServicepoint = new Servicepoint();
    Servicepoint newServicepoint = new Servicepoint()
      .withHoldShelfExpiryPeriod(new HoldShelfExpiryPeriod());

    var updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      updateEvent(oldServicepoint, newServicepoint, TENANT));

    assertFalse(updateEventProcessor.validateEventEntity());
  }

  private void processEventToThrowException(ServicePointSynchronizationEventProcessor processor,
    VertxTestContext testContext) {

    processor.processEvent(null, "")
      .onComplete(ar ->
        testContext.verify(() -> {
          assertTrue(ar.cause() instanceof RuntimeException);
          testContext.completeNow();
        }));
  }
}
