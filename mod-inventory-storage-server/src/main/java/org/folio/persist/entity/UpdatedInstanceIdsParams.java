package org.folio.persist.entity;

import org.apache.commons.lang3.StringUtils;

/**
 * Selects the instances for the inventory hierarchy.
 *
 * @param startDate ISO 8601 date time, instances changed from then on, may be empty
 * @param endDate ISO 8601 date time, instances changed until then, may be empty
 * @param deletedRecordSupport whether to include the deleted instances
 * @param skipSuppressedFromDiscoveryRecords whether to leave out the instances suppressed from discovery
 * @param onlyInstanceUpdateDate whether only the update date of the instance counts, not of its holdings and items
 * @param source the source of the instances, null for all sources
 */
public record UpdatedInstanceIdsParams(String startDate, String endDate, boolean deletedRecordSupport,
                                       boolean skipSuppressedFromDiscoveryRecords, boolean onlyInstanceUpdateDate,
                                       String source) {

  /**
   * Whether the params select all instances, because there is neither a start nor an end date.
   */
  public boolean isInitialLoad() {
    return StringUtils.isEmpty(startDate) && StringUtils.isEmpty(endDate);
  }
}
