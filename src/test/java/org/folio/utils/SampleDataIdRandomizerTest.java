package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SampleDataIdRandomizerTest {

  private SampleDataIdRandomizer randomizer;

  @BeforeEach
  void setUp() {
    randomizer = new SampleDataIdRandomizer();
  }

  @Test
  void testRandomizeSimpleId() {
    var originalId = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
    var jsonContent = String.format("{\"id\": \"%s\", \"title\": \"Test Instance\"}", originalId);

    var randomized = randomizer.randomizeInstanceId(jsonContent);
    var result = new JsonObject(randomized);

    assertNotNull(result.getString("id"));
    assertNotEquals(originalId, result.getString("id"));
    assertEquals("Test Instance", result.getString("title"));
  }

  @Test
  void testHoldingsRecordIdNotRandomized() {
    var jsonContent = """
      {
        "id": "7fbd5d84-62d1-44c6-9c45-6cb173998bbd",
        "instanceId": "5bf370e0-8cca-4d9c-82e4-5f4988a5e2e1",
        "holdingsRecordId": "65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61",
        "callNumber": "ABC 123"
      }
      """;

    var randomized = randomizer.updateInstanceIdReferences(jsonContent);
    var result = new JsonObject(randomized);

    // Holdings ID should NOT be randomized (we're only updating references)
    assertEquals("7fbd5d84-62d1-44c6-9c45-6cb173998bbd", result.getString("id"));
    // holdingsRecordId should also NOT be randomized
    assertEquals("65cb2bf0-d4c2-4886-8ad0-b76f1ba75d61", result.getString("holdingsRecordId"));
    // instanceId should stay unchanged (no mapping exists for it)
    assertEquals("5bf370e0-8cca-4d9c-82e4-5f4988a5e2e1", result.getString("instanceId"));
  }

  @Test
  void testInstanceIdReferenceMapping() {
    var instanceId = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
    // First randomize an instance record (creates mapping)
    var instanceJson = String.format("{\"id\": \"%s\", \"title\": \"Test Instance\"}", instanceId);
    var randomizedInstance = randomizer.randomizeInstanceId(instanceJson);
    var instance = new JsonObject(randomizedInstance);
    var newInstanceId = instance.getString("id");

    // Then process a holdings record that references this instance
    var holdingsJson = String.format(
      "{\"id\": \"e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19\", \"instanceId\": \"%s\", \"callNumber\": \"ABC 123\"}",
      instanceId);
    var randomizedHoldings = randomizer.updateInstanceIdReferences(holdingsJson);
    var holdings = new JsonObject(randomizedHoldings);

    // The instanceId reference should be updated to the new randomized ID
    assertEquals(newInstanceId, holdings.getString("instanceId"));
    assertNotEquals(instanceId, holdings.getString("instanceId"));
    // Holdings own ID should NOT be randomized
    assertEquals("e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19", holdings.getString("id"));
  }

  @Test
  void testFieldsContainingInstanceId() {
    var superInstanceId = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
    var subInstanceId = "5bf370e0-8cca-4d9c-82e4-5f4988a5e2e1";

    // First randomize the instances
    randomizer.randomizeInstanceId(String.format("{\"id\": \"%s\", \"title\": \"Super Instance\"}", superInstanceId));
    randomizer.randomizeInstanceId(String.format("{\"id\": \"%s\", \"title\": \"Sub Instance\"}", subInstanceId));

    // Then process a relationship record with multiple fields containing "instanceId"
    var relationshipJson = String.format("""
      {
        "id": "e95b3807-ef1a-4588-b685-50ec38b4973a",
        "superInstanceId": "%s",
        "subInstanceId": "%s",
        "instanceRelationshipTypeId": "30773a27-b485-4dab-aeb6-b8c04fa3cb17"
      }
      """, superInstanceId, subInstanceId);

    var randomized = randomizer.updateInstanceIdReferences(relationshipJson);
    var result = new JsonObject(randomized);

    // The relationship record's ID should NOT be changed (we only update references)
    assertEquals("e95b3807-ef1a-4588-b685-50ec38b4973a", result.getString("id"));
    // Any field containing "instanceId" should be updated if mapping exists
    assertNotEquals(superInstanceId, result.getString("superInstanceId"));
    assertNotEquals(subInstanceId, result.getString("subInstanceId"));
    // instanceRelationshipTypeId should NOT be changed (it's a type ID, not an instance reference)
    assertEquals("30773a27-b485-4dab-aeb6-b8c04fa3cb17", result.getString("instanceRelationshipTypeId"));
  }

  @Test
  void testNestedObjects() {
    var instanceId = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
    var jsonContent = String.format("""
      {
        "id": "%s",
        "title": "Test Instance",
        "metadata": {
          "createdByUserId": "5bf370e0-8cca-4d9c-82e4-5f4988a5e2e1"
        }
      }
      """, instanceId);

    var randomized = randomizer.randomizeInstanceId(jsonContent);
    var result = new JsonObject(randomized);

    // Instance ID should be randomized
    assertNotEquals(instanceId, result.getString("id"));
    // Other IDs should NOT be randomized
    assertEquals("5bf370e0-8cca-4d9c-82e4-5f4988a5e2e1",
      result.getJsonObject("metadata").getString("createdByUserId"));
  }

  @Test
  void testNonInstanceFieldsPreserved() {
    var instanceId = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
    var jsonContent = String.format("""
      {
        "id": "%s",
        "barcode": "12345",
        "title": "Test Book",
        "holdingsRecordId": "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19",
        "permanentLocationId": "fcd64ce1-6995-48f0-840e-89ffa2288371"
      }
      """, instanceId);

    var randomized = randomizer.randomizeInstanceId(jsonContent);
    var result = new JsonObject(randomized);

    // Instance ID should be randomized
    assertNotEquals(instanceId, result.getString("id"));
    // All other fields should remain unchanged
    assertEquals("12345", result.getString("barcode"));
    assertEquals("Test Book", result.getString("title"));
    assertEquals("e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19", result.getString("holdingsRecordId"));
    assertEquals("fcd64ce1-6995-48f0-840e-89ffa2288371", result.getString("permanentLocationId"));
  }

  @Test
  void testInvalidJsonReturnsOriginal() {
    var invalidJson = "This is not valid JSON";
    var result = randomizer.randomizeInstanceId(invalidJson);
    assertEquals(invalidJson, result);
  }

  @Test
  void testClearMappings() {
    var id = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
    var jsonContent = String.format("{\"id\": \"%s\", \"title\": \"Test\"}", id);

    var randomized1 = randomizer.randomizeInstanceId(jsonContent);
    var result1 = new JsonObject(randomized1);
    var newId1 = result1.getString("id");

    randomizer.clearMappings();

    var randomized2 = randomizer.randomizeInstanceId(jsonContent);
    var result2 = new JsonObject(randomized2);
    var newId2 = result2.getString("id");

    // After clearing mappings, the same original ID should map to a different new ID
    assertNotEquals(newId1, newId2);
  }

  @Test
  void testConsistentMappingAcrossReferences() {
    var instanceId = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";

    // Randomize an instance
    var instanceJson = String.format("{\"id\": \"%s\", \"title\": \"Test\"}", instanceId);
    var randomizedInstance = randomizer.randomizeInstanceId(instanceJson);
    var instance = new JsonObject(randomizedInstance);
    var newInstanceId = instance.getString("id");

    // Verify the same ID is used consistently across multiple references
    var holdingsJson = String.format("{\"id\": \"h1\", \"instanceId\": \"%s\"}", instanceId);
    var itemJson = String.format("{\"id\": \"i1\", \"instanceId\": \"%s\"}", instanceId);
    var relationshipJson = String.format("{\"id\": \"r1\", \"superInstanceId\": \"%s\"}", instanceId);

    var holdings = new JsonObject(randomizer.updateInstanceIdReferences(holdingsJson));
    var item = new JsonObject(randomizer.updateInstanceIdReferences(itemJson));
    var relationship = new JsonObject(randomizer.updateInstanceIdReferences(relationshipJson));

    // All references should point to the same new instance ID
    assertEquals(newInstanceId, holdings.getString("instanceId"));
    assertEquals(newInstanceId, item.getString("instanceId"));
    assertEquals(newInstanceId, relationship.getString("superInstanceId"));
  }
}

