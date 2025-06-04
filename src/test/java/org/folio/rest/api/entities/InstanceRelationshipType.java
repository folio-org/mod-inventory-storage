package org.folio.rest.api.entities;

public class InstanceRelationshipType extends JsonEntity {

  public static final String NAME_KEY = "name";

  public InstanceRelationshipType(String id, String name) {
    super.setProperty(ID_KEY, id);
    super.setProperty(NAME_KEY, name);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }
}
