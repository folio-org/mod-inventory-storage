package org.folio.rest.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Instance;

/**
 * Class that provides utility methods for work with instance.
 */
public final class InstanceBulkProcessingUtil {

  private static final ObjectMapper OBJECT_MAPPER;

  static {
    OBJECT_MAPPER = new ObjectMapper();
    OBJECT_MAPPER.addMixIn(Instance.class, InstanceMixIn.class);
  }

  private InstanceBulkProcessingUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  /**
   * Maps extended instance JSON representation from bulk instances data to {@link Instance} object
   * corresponding to the inventory storage module schema.
   *
   * @param extendedInstanceRepresentation - extended instance representation as json object
   * @return {@link Instance} object
   */
  public static Instance mapBulkInstanceRecordToInstance(JsonObject extendedInstanceRepresentation) {
    return OBJECT_MAPPER.convertValue(extendedInstanceRepresentation.getMap(), Instance.class);
  }

  /**
   * Copies fields that are not controlled by underlying MARC record from the specified {@code sourceInstance}
   * to the {@code targetInstance}.
   *
   * @param targetInstance - instance object that is populated with not controlled by MARC fields values
   * @param sourceInstance - instance object from which the field values will be copied
   */
  public static void copyNonMarcControlledFields(Instance targetInstance, Instance sourceInstance) {
    targetInstance.setStaffSuppress(sourceInstance.getStaffSuppress());
    targetInstance.setDiscoverySuppress(sourceInstance.getDiscoverySuppress());
    targetInstance.setCatalogedDate(sourceInstance.getCatalogedDate());
    targetInstance.setStatusId(sourceInstance.getStatusId());
    targetInstance.setStatusUpdatedDate(sourceInstance.getStatusUpdatedDate());
    targetInstance.setStatisticalCodeIds(sourceInstance.getStatisticalCodeIds());
    targetInstance.setAdministrativeNotes(sourceInstance.getAdministrativeNotes());
    targetInstance.setNatureOfContentTermIds(sourceInstance.getNatureOfContentTermIds());
    targetInstance.setTags(sourceInstance.getTags());
    targetInstance.setPreviouslyHeld(sourceInstance.getPreviouslyHeld());
  }

  @JsonIgnoreProperties({"precedingTitles", "succeedingTitles", "isBoundWith", "parentInstances", "childInstances"})
  private interface InstanceMixIn {}

}
