package org.folio.rest.api.entities;

public class ContributorNameType extends JsonEntity {
  public static final String NAME_KEY = "name";
  public static final String ORDERING_KEY = "ordering";

  public ContributorNameType(String name, String ordering) {
    super.setProperty(NAME_KEY, name);
    super.setProperty(ORDERING_KEY, ordering);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }
}
