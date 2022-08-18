package org.folio.rest.api.entities;

public class AuthoritySourceFile extends JsonEntity {
  public static final String NAME_KEY = "name";
  public static final String CODE_KEY = "code";
  public static final String TYPE_KEY = "type";

  public AuthoritySourceFile(String name, String code, String type) {
    super.setProperty(NAME_KEY, name);
    super.setProperty(CODE_KEY, code);
    super.setProperty(TYPE_KEY, type);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }

}
