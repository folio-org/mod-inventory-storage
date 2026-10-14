package org.folio.services.instance;

import java.util.Optional;

/**
 * Recognises a CQL query that looks up exactly one instance by HRID.
 */
public final class InventoryViewHridQuery {

  private InventoryViewHridQuery() {
  }

  public static Optional<String> extractHrid(String cql) {
    return Optional.empty();
  }
}
