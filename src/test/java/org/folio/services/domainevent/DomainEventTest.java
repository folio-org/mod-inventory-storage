package org.folio.services.domainevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DomainEventTest {

  @Test
  void testCreateEvent() {
    String newEntity = "newEntity";
    String tenant = "tenant";
    DomainEvent<String> event = DomainEvent.createEvent(newEntity, tenant);

    assertNotNull(event.getEventId());
    assertNotNull(event.getEventTs());
    assertEquals(newEntity, event.getNewEntity());
    assertNull(event.getOldEntity());
    assertEquals(DomainEventType.CREATE, event.getType());
    assertEquals(tenant, event.getTenant());
  }

  @Test
  void testUpdateEvent() {
    String oldEntity = "oldEntity";
    String newEntity = "newEntity";
    String tenant = "tenant";
    DomainEvent<String> event = DomainEvent.updateEvent(oldEntity, newEntity, tenant);

    assertNotNull(event.getEventId());
    assertNotNull(event.getEventTs());
    assertEquals(newEntity, event.getNewEntity());
    assertEquals(oldEntity, event.getOldEntity());
    assertEquals(DomainEventType.UPDATE, event.getType());
    assertEquals(tenant, event.getTenant());
  }

  @Test
  void testDeleteEvent() {
    String oldEntity = "oldEntity";
    String tenant = "tenant";
    DomainEvent<String> event = DomainEvent.deleteEvent(oldEntity, tenant);

    assertNotNull(event.getEventId());
    assertNotNull(event.getEventTs());
    assertNull(event.getNewEntity());
    assertEquals(oldEntity, event.getOldEntity());
    assertEquals(DomainEventType.DELETE, event.getType());
    assertEquals(tenant, event.getTenant());
  }

  @Test
  void testDeleteAllEvent() {
    String tenant = "tenant";
    DomainEvent<String> event = DomainEvent.deleteAllEvent(tenant);

    assertNotNull(event.getEventId());
    assertNotNull(event.getEventTs());
    assertNull(event.getNewEntity());
    assertNull(event.getOldEntity());
    assertEquals(DomainEventType.DELETE_ALL, event.getType());
    assertEquals(tenant, event.getTenant());
  }

  @Test
  void testReindexEvent() {
    String tenant = "tenant";
    DomainEvent<String> event = DomainEvent.reindexEvent(tenant);

    assertNotNull(event.getEventId());
    assertNotNull(event.getEventTs());
    assertNull(event.getNewEntity());
    assertNull(event.getOldEntity());
    assertEquals(DomainEventType.REINDEX, event.getType());
    assertEquals(tenant, event.getTenant());
  }

  @Test
  void testAsyncMigrationEvent() {
    String job = "job";
    String tenant = "tenant";
    DomainEvent<String> event = DomainEvent.asyncMigrationEvent(job, tenant);

    assertNotNull(event.getEventId());
    assertNotNull(event.getEventTs());
    assertEquals(job, event.getNewEntity());
    assertNull(event.getOldEntity());
    assertEquals(DomainEventType.MIGRATION, event.getType());
    assertEquals(tenant, event.getTenant());
  }
}
