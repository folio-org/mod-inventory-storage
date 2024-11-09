package org.folio.services.consortium.processor;

import static org.junit.Assert.assertTrue;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.DomainEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class ServicePointSynchronizationEventProcessorTest {
  private ServicePointSynchronizationUpdateEventProcessor updateEventProcessor;
  private ServicePointSynchronizationCreateEventProcessor createEventProcessor;

  @BeforeEach
  void setUp() {
    Servicepoint newServicepoint = new Servicepoint();
    Servicepoint oldServicepoint = new Servicepoint();
    updateEventProcessor = new ServicePointSynchronizationUpdateEventProcessor(new DomainEvent<>(
      oldServicepoint, newServicepoint, DomainEventType.UPDATE, "tenant"));
    createEventProcessor = new ServicePointSynchronizationCreateEventProcessor(
      new DomainEvent<>(oldServicepoint, newServicepoint, DomainEventType.CREATE, "tenant")
    );
  }

  @Test
  void shouldFailToUpdateEventDueToNullServicePointService(VertxTestContext testContext) {
    processEventToThrowException(updateEventProcessor, testContext);
  }

  @Test
  void shouldFailToCreateEventDueToNullServicePointService(VertxTestContext testContext) {
    processEventToThrowException(createEventProcessor, testContext);
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
