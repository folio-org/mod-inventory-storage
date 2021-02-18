package org.folio.services.domainevent;

public class ReindexInstanceEvent {
  private final String id;
  private final DomainEventType type;
  private final String tenant;

  public ReindexInstanceEvent(String id, String tenant) {
    this.id = id;
    this.type = DomainEventType.REINDEX;
    this.tenant = tenant;
  }

  public String getId() {
    return id;
  }

  public DomainEventType getType() {
    return type;
  }

  public String getTenant() {
    return tenant;
  }
}
