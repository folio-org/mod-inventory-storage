package org.folio.persist.entity;

/**
 * A record removed by a delete-by-CQL, as needed to publish its domain event.
 *
 * @param instanceId the id of the instance the record belongs to (the record's own id for instances)
 * @param json       the removed record as JSON
 */
public record DeletedRecord(String instanceId, String json) {
}
