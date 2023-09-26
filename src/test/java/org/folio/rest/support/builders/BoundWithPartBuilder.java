package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.With;

@With
@AllArgsConstructor
@ToString
public final class BoundWithPartBuilder extends JsonRequestBuilder implements Builder {
  private final UUID holdingsRecordId;
  private final UUID itemId;

  @Override
  public JsonObject create() {
    final var json = new JsonObject();

    put(json, "holdingsRecordId", holdingsRecordId);
    put(json, "itemId", itemId);

    return json;
  }
}
