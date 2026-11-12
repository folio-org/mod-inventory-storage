package org.folio.persist.entity;

/**
 * The instance a holdings record belongs to.
 *
 * @param instanceId     the id of the instance
 * @param instanceSource the {@code source} of the instance
 */
public record HoldingsInstance(String instanceId, String instanceSource) {
}
