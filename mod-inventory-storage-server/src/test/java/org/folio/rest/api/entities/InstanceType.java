package org.folio.rest.api.entities;

public class InstanceType extends JsonEntity {
  public static final String NAME_KEY = "name";
  public static final String CODE_KEY = "code";
  public static final String SOURCE_KEY = "source";

  public InstanceType(String name, String code, String source) {
    super.setProperty(NAME_KEY, name);
    super.setProperty(CODE_KEY, code);
    super.setProperty(SOURCE_KEY, source);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }
}
