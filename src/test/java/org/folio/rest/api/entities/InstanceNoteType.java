package org.folio.rest.api.entities;

public class InstanceNoteType extends JsonEntity {
  public static final String NAME_KEY = "name";
  public static final String SOURCE_KEY = "source";

  public InstanceNoteType(String name, String source) {
    super.setProperty(NAME_KEY, name);
    super.setProperty(SOURCE_KEY, source);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }
}
