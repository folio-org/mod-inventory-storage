package org.folio.rest.impl;

/**
 * Request paths for {@code *IT} tests, relative to the shared verticle's own base URL (see
 * {@link BaseIntegrationTest}). Not to be confused with {@code org.folio.rest.support.http.InterfaceUrls},
 * which builds absolute URLs against the legacy {@code rest.api} stack's own module instance.
 */
final class ResourcePaths {

  static final String INSTANCE_TYPES = "/instance-types";
  static final String INSTANCE_RELATIONSHIP_TYPES = "/instance-relationship-types";
  static final String INSTANCES = "/instance-storage/instances";
  static final String INSTANCE_RELATIONSHIPS = "/instance-storage/instance-relationships";
  static final String PRECEDING_SUCCEEDING_TITLES = "/preceding-succeeding-titles";

  private ResourcePaths() {
  }
}
