package org.folio.rest.api.entities;

import java.util.List;

import io.vertx.core.json.JsonArray;

public class PrecedingSucceedingTitles extends JsonEntity {
  public static final String TITLES_KEY = "precedingSucceedingTitles";
  public static final String TOTAL_RECORDS_KEY = "totalRecords";

  public PrecedingSucceedingTitles(List<PrecedingSucceedingTitle> titles) {
    JsonArray titlesArray = new JsonArray();
    titles.stream()
      .map(JsonEntity::getJson)
      .forEach(titlesArray::add);
    setProperty(TITLES_KEY, titlesArray);
    setProperty(TOTAL_RECORDS_KEY, titles.size());
  }

  @Override
  public PrecedingSucceedingTitles put(String key, Object value) {
    setProperty(key, value);
    return this;
  }
}
