package org.folio.rest.api.entities;

import java.util.List;

public class AuthoritySourceFile extends JsonEntity {
  public static final String NAME_KEY = "name";
  public static final String CODES_KEY = "codes";
  public static final String TYPE_KEY = "type";
  public static final String BASE_URL_KEY = "baseUrl";
  public static final String SOURCE_KEY = "source";

  public AuthoritySourceFile(String name, List<String> codes, String type, String baseUrl, String source) {
    super.setProperty(NAME_KEY, name);
    super.setProperty(CODES_KEY, codes);
    super.setProperty(TYPE_KEY, type);
    super.setProperty(BASE_URL_KEY, baseUrl);
    super.setProperty(SOURCE_KEY, source);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }

}
