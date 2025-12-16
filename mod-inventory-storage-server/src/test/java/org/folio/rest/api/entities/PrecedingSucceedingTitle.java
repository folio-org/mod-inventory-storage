package org.folio.rest.api.entities;

import io.vertx.core.json.JsonArray;

public class PrecedingSucceedingTitle extends JsonEntity {
  public static final String PRECEDING_INSTANCE_ID_KEY = "precedingInstanceId";
  public static final String SUCCEEDING_INSTANCE_ID_KEY = "succeedingInstanceId";
  public static final String TITLE_KEY = "title";
  public static final String HRID_KEY = "hrid";
  public static final String IDENTIFIERS_KEY = "identifiers";

  public PrecedingSucceedingTitle(String precedingInstanceId, String succeedingInstanceId,
                                  String title,
                                  String hrid, JsonArray identifiers) {
    setProperty(PRECEDING_INSTANCE_ID_KEY, precedingInstanceId);
    setProperty(SUCCEEDING_INSTANCE_ID_KEY, succeedingInstanceId);
    setProperty(TITLE_KEY, title);
    setProperty(HRID_KEY, hrid);
    setProperty(IDENTIFIERS_KEY, identifiers);
  }

  @Override
  public PrecedingSucceedingTitle put(String key, Object value) {
    setProperty(key, value);
    return this;
  }
}
