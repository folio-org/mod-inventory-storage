package org.folio.rest.api.entities;

public class ModeOfIssuance extends JsonEntity {

  public static final String NAME_KEY = "name";

  public ModeOfIssuance(String name) {
    super.setProperty(NAME_KEY, name);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }
}
