package org.folio.rest.impl;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

/**
 * Creates item-storage records and their reference data (statistical codes) for {@code *IT}
 * tests, rather than relying on shared class-level state (see docs/testing.md). Each call
 * creates a fresh record with a random id.
 */
final class ItemStorageFixtures {

  private ItemStorageFixtures() {
  }

  static String createStatisticalCode(HttpClient client) {
    var typeId = UUID.randomUUID().toString();
    var type = new JsonObject().put("id", typeId).put("name", "test statistical code type " + typeId)
      .put("source", "local");
    BaseIntegrationTest.get(BaseIntegrationTest.doPost(client, ResourcePaths.STATISTICAL_CODE_TYPES, type));

    var id = UUID.randomUUID().toString();
    var code = new JsonObject().put("id", id).put("code", id.substring(0, 8))
      .put("name", "test statistical code " + id).put("statisticalCodeTypeId", typeId).put("source", "local");
    BaseIntegrationTest.get(BaseIntegrationTest.doPost(client, ResourcePaths.STATISTICAL_CODES, code));

    return id;
  }
}
