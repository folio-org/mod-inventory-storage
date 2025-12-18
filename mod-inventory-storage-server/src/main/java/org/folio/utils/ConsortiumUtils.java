package org.folio.utils;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import java.util.Map;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.services.caches.ConsortiumData;

public final class ConsortiumUtils {

  private ConsortiumUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static boolean isCentralTenant(Map<String, String> headers, ConsortiumData consortiumData) {
    Map<String, String> caseInsensitiveHeaders = new CaseInsensitiveMap<>(headers);
    String tenantId = caseInsensitiveHeaders.get(TENANT);
    return isCentralTenant(tenantId, consortiumData);
  }

  public static boolean isCentralTenant(String tenantId, ConsortiumData consortiumData) {
    return tenantId.equals(consortiumData.centralTenantId());
  }
}
