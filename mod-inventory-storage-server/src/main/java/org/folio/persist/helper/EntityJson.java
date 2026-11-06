package org.folio.persist.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.json.JsonObject;
import org.folio.rest.persist.PostgresClient;

/**
 * Converts domain objects to the JSON that is stored in the database.
 */
public final class EntityJson {

  private EntityJson() {
  }

  /**
   * Converts the object with the same mapper that the database layer uses to store it.
   */
  public static JsonObject toJsonObject(Object entity) throws JsonProcessingException {
    return PostgresClient.pojo2JsonObject(entity);
  }
}
