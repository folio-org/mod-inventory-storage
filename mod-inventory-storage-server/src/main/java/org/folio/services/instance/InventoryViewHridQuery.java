package org.folio.services.instance;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Recognises a CQL query that looks up exactly one instance by HRID:
 * a single term, index {@code instance.hrid} or {@code instanceHrid}, relation {@code ==} or
 * {@code =}, no modifiers, and a term without CQL wildcards or escapes. Such a query can be
 * answered from the indexed {@code instance_hrid} column of the inventory view.
 */
public final class InventoryViewHridQuery {

  private static final Set<String> HRID_INDEXES = Set.of("instance.hrid", "instanceHrid");
  private static final Set<String> RELATIONS = Set.of("==", "=");
  private static final char[] UNSUPPORTED_TERM_CHARS = {'*', '?', '^', '\\'};

  private InventoryViewHridQuery() {
  }

  /**
   * Returns the HRID if {@code cql} qualifies for the fast path, otherwise empty. Invalid CQL
   * is not an error here: the caller falls back to the generic path, which reports it.
   */
  public static Optional<String> extractHrid(String cql) {
    if (StringUtils.isBlank(cql)) {
      return Optional.empty();
    }
    try {
      if (new CQLParser().parse(cql) instanceof CQLTermNode term && isHridLookup(term)) {
        return Optional.of(term.getTerm());
      }
      return Optional.empty();
    } catch (CQLParseException | IOException e) {
      return Optional.empty();
    }
  }

  private static boolean isHridLookup(CQLTermNode term) {
    return HRID_INDEXES.contains(term.getIndex())
      && RELATIONS.contains(term.getRelation().getBase())
      && term.getRelation().getModifiers().isEmpty()
      && StringUtils.isNotEmpty(term.getTerm())
      && !StringUtils.containsAny(term.getTerm(), UNSUPPORTED_TERM_CHARS);
  }
}
