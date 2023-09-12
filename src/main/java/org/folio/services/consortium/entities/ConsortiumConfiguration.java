package org.folio.services.consortium.entities;

public class ConsortiumConfiguration {
  private String centralTenantId;
  private String consortiumId;

  public ConsortiumConfiguration(String centralTenantId, String consortiumId) {
    this.centralTenantId = centralTenantId;
    this.consortiumId = consortiumId;
  }

  public String getCentralTenantId() {
    return centralTenantId;
  }

  public void setCentralTenantId(String centralTenantId) {
    this.centralTenantId = centralTenantId;
  }

  public String getConsortiumId() {
    return consortiumId;
  }

  public void setConsortiumId(String consortiumId) {
    this.consortiumId = consortiumId;
  }
}
