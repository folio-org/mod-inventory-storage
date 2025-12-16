package org.folio.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class to randomize instance IDs in sample data JSON files.
 * This ensures that different tenants don't have instances with the same IDs,
 * preventing them from being treated as copies.
 *
 * <p>
 * The randomizer maintains a mapping of old instance IDs to new randomized IDs,
 * ensuring that all references to an instance (via instanceId field) are updated
 * consistently across holdings, items, and instance relationships.
 */
public class SampleDataIdRandomizer {

  private static final Logger log = LogManager.getLogger();

  // Pattern to match UUID format
  private static final Pattern UUID_PATTERN = Pattern.compile(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
  );

  // Mapping of old instance IDs to new randomized IDs
  private final Map<String, String> instanceIdMapping = new HashMap<>();

  /**
   * Randomizes the instance ID in the given JSON content.
   * Only randomizes the top-level "id" field. Use this when loading instance records.
   *
   * @param jsonContent the JSON content as a string
   * @return the JSON content with randomized instance ID
   */
  public String randomizeInstanceId(String jsonContent) {
    try {
      var jsonObject = new JsonObject(jsonContent);
      if (jsonObject.containsKey("id")) {
        var value = jsonObject.getValue("id");
        if (value instanceof String strValue && isUuid(strValue)) {
          jsonObject.put("id", getOrCreateMapping(strValue));
        }
      }
      return jsonObject.encodePrettily();
    } catch (Exception e) {
      log.warn("Failed to randomize instance ID in JSON content: {}", e.getMessage());
      return jsonContent;
    }
  }

  /**
   * Updates instance ID references in the given JSON content.
   * Traverses the JSON and updates any field containing "instanceId" in its name.
   * Use this when loading entities that reference instances (holdings, items, relationships).
   *
   * @param jsonContent the JSON content as a string
   * @return the JSON content with updated instance ID references
   */
  public String updateInstanceIdReferences(String jsonContent) {
    try {
      var jsonObject = new JsonObject(jsonContent);
      updateInstanceIdFields(jsonObject);
      processNestedObjects(jsonObject);
      return jsonObject.encodePrettily();
    } catch (Exception e) {
      log.warn("Failed to update instance ID references in JSON content: {}", e.getMessage());
      return jsonContent;
    }
  }

  /**
   * Updates fields containing "instanceId" in their name with mapped values.
   */
  private void updateInstanceIdFields(JsonObject jsonObject) {
    for (var fieldName : jsonObject.fieldNames()) {
      if (fieldName.contains("instanceId") || fieldName.contains("InstanceId")) {
        var value = jsonObject.getValue(fieldName);
        if (value instanceof String strValue && isUuid(strValue)) {
          var newId = instanceIdMapping.get(strValue);
          if (newId != null) {
            jsonObject.put(fieldName, newId);
          }
        }
      }
    }
  }

  /**
   * Processes nested objects and arrays to update instance ID references.
   */
  private void processNestedObjects(JsonObject jsonObject) {
    for (var key : jsonObject.fieldNames()) {
      var value = jsonObject.getValue(key);
      if (value instanceof JsonObject nestedObject) {
        updateInstanceIdFields(nestedObject);
        processNestedObjects(nestedObject);
      } else if (value instanceof JsonArray nestedArray) {
        processNestedArray(nestedArray);
      }
    }
  }

  /**
   * Processes a JsonArray recursively to update instance ID references.
   */
  private void processNestedArray(JsonArray jsonArray) {
    for (var i = 0; i < jsonArray.size(); i++) {
      var item = jsonArray.getValue(i);
      if (item instanceof JsonObject jsonObject) {
        updateInstanceIdFields(jsonObject);
        processNestedObjects(jsonObject);
      } else if (item instanceof JsonArray nestedArray) {
        processNestedArray(nestedArray);
      }
    }
  }

  /**
   * Checks if a string is a valid UUID.
   *
   * @param value the string to check
   * @return true if the string is a valid UUID, false otherwise
   */
  private boolean isUuid(String value) {
    return value != null && UUID_PATTERN.matcher(value).matches();
  }

  /**
   * Gets an existing instance ID mapping or creates a new one.
   * This ensures consistency across related entities.
   *
   * @param oldInstanceId the original instance ID
   * @return the mapped new instance ID
   */
  private String getOrCreateMapping(String oldInstanceId) {
    return instanceIdMapping.computeIfAbsent(oldInstanceId, k -> UUID.randomUUID().toString());
  }

  /**
   * Clears the instance ID mapping cache.
   * Useful when processing a new independent dataset.
   */
  public void clearMappings() {
    instanceIdMapping.clear();
  }
}

