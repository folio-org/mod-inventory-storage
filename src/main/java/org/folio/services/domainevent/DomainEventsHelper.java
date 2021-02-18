package org.folio.services.domainevent;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DomainEventsHelper {
  private static final Set<String> FORWARDER_HEADERS = Set.of(URL.toLowerCase(),
    TENANT.toLowerCase());

  private DomainEventsHelper() {}

  public static Map<String, String> getHeadersToForward(Map<String, String> okapiHeaders) {
    return okapiHeaders.entrySet().stream()
      .filter(entry -> FORWARDER_HEADERS.contains(entry.getKey().toLowerCase()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
