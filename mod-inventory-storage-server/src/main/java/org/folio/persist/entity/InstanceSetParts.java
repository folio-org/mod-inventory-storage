package org.folio.persist.entity;

/**
 * The parts to include for each instance of an instance set.
 */
public record InstanceSetParts(boolean instance, boolean holdingsRecords, boolean items,
                               boolean precedingTitles, boolean succeedingTitles,
                               boolean superInstanceRelationships, boolean subInstanceRelationships) {
}
