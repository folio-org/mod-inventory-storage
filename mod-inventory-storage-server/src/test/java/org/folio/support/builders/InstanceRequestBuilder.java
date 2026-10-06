package org.folio.support.builders;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

public class InstanceRequestBuilder extends JsonRequestBuilder implements Builder {

  private final UUID id;
  private final String title;
  private final String source;
  private final UUID instanceTypeId;

  public InstanceRequestBuilder() {
    this(null, "a test instance", "TEST", null);
  }

  private InstanceRequestBuilder(UUID id, String title, String source, UUID instanceTypeId) {
    this.id = id;
    this.title = title;
    this.source = source;
    this.instanceTypeId = instanceTypeId;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", id);
    put(request, "title", title);
    put(request, "source", source);
    put(request, "instanceTypeId", instanceTypeId);

    return request;
  }

  public InstanceRequestBuilder withId(UUID id) {
    return new InstanceRequestBuilder(id, this.title, this.source, this.instanceTypeId);
  }

  public InstanceRequestBuilder withTitle(String title) {
    return new InstanceRequestBuilder(this.id, title, this.source, this.instanceTypeId);
  }

  public InstanceRequestBuilder withSource(String source) {
    return new InstanceRequestBuilder(this.id, this.title, source, this.instanceTypeId);
  }

  public InstanceRequestBuilder withInstanceTypeId(UUID instanceTypeId) {
    return new InstanceRequestBuilder(this.id, this.title, this.source, instanceTypeId);
  }
}
