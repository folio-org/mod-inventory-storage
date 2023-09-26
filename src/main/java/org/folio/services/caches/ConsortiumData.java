package org.folio.services.caches;

public class ConsortiumData {

  private String centralTenantId;
  private String consortiumId;

  public ConsortiumData(String centralTenantId, String consortiumId) {
    this.centralTenantId = centralTenantId;
    this.consortiumId = consortiumId;
  }

  public String getCentralTenantId() {
    return centralTenantId;
  }

  public String getConsortiumId() {
    return consortiumId;
  }
}
