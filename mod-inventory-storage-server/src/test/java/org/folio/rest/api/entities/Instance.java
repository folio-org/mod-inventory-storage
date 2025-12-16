package org.folio.rest.api.entities;

public class Instance extends JsonEntity {

  public static final String TITLE_KEY = "title";
  public static final String SOURCE_KEY = "source";
  public static final String INSTANCE_TYPE_ID = "instanceTypeId";

  public static final String IDENTIFIERS_KEY = "identifiers";
  public static final String CONTRIBUTORS_KEY = "contributors";
  public static final String STATISTICAL_CODE_IDS_KEY = "statisticalCodeIds";

  public Instance() { }

  public Instance(String title, String source, String instanceTypeId) {
    super.setProperty(TITLE_KEY, title);
    super.setProperty(SOURCE_KEY, source);
    super.setProperty(INSTANCE_TYPE_ID, instanceTypeId);
  }

  @Override
  public Instance put(String key, Object value) {
    setProperty(key, value);
    return this;
  }
}
