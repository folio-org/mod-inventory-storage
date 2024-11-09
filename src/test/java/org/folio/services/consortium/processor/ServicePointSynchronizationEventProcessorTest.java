package org.folio.services.consortium.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.DomainEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class ServicePointSynchronizationEventProcessorTest {
  private static final String TENANT = "tenant";
  private ServicePointSynchronizationUpdateEventProcessor updateEventProcessor;
  private ServicePointSynchronizationCreateEventProcessor createEventProcessor;

  @BeforeEach
  void setUp() {
    Servicepoint newServicepoint = new Servicepoint();
    Servicepoint oldServicepoint = new Servicepoint();

    createEventProcessor = new ServicePointSynchronizationCreateEventProcessor(
      new DomainEvent<>(oldServicepoint, newServicepoint, DomainEventType.CREATE, TENANT)
    );
  }

  @Test
  void shouldFailToUpdateEventDueToProcessEventException(VertxTestContext testContext) {
    updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(new DomainEvent<>(
      new Servicepoint(), new Servicepoint(), DomainEventType.UPDATE, TENANT));
    processEventToThrowException(updateEventProcessor, testContext);
  }

  @Test
  void shouldFailToCreateEventDueToProcessEventException(VertxTestContext testContext) {
    updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(new DomainEvent<>(
      new Servicepoint(), new Servicepoint(), DomainEventType.UPDATE, TENANT));
    processEventToThrowException(createEventProcessor, testContext);
  }

  @Test
  void shouldReturnFalseIfBothServicePointsAreNull() {
    updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      new DomainEvent<>(null, null, DomainEventType.UPDATE, TENANT));

    assertFalse(updateEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnFalseIfServicePointsAreIdentical() {
    Servicepoint servicepoint = new Servicepoint();
    updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      new DomainEvent<>(servicepoint, servicepoint, DomainEventType.UPDATE, TENANT));

    assertFalse(updateEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnTrueIfNewServicePointIsValid() {
    Servicepoint oldServicepoint = new Servicepoint().withId(UUID.randomUUID().toString());
    Servicepoint newServicepoint = new Servicepoint().withId(UUID.randomUUID().toString());

    updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      new DomainEvent<>(oldServicepoint, newServicepoint, DomainEventType.UPDATE, TENANT));

    assertTrue(updateEventProcessor.validateEventEntity());
  }

  @Test
  void shouldReturnFalseIfValidationMessageIsNotNull() {
    Servicepoint oldServicepoint = new Servicepoint();
    Servicepoint newServicepoint = new Servicepoint()
      .withHoldShelfExpiryPeriod(new HoldShelfExpiryPeriod());

    updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(
      new DomainEvent<>(oldServicepoint, newServicepoint, DomainEventType.UPDATE, TENANT));

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
