package org.folio.services.caches;

import java.util.List;

public record ConsortiumData(String centralTenantId, String consortiumId, List<String> memberTenants) {

  public ConsortiumData(String centralTenantId, String consortiumId, List<String> memberTenants) {
    this.centralTenantId = centralTenantId;
    this.consortiumId = consortiumId;
    this.memberTenants = memberTenants;
  }
}
