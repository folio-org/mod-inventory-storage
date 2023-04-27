/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.folio.rest.api.entities;


public class StatisticalCode extends JsonEntity {
  public static final String NAME_KEY = "name";
  public static final String CODE_KEY = "code";
  public static final String SOURCE_KEY = "source";
  public static final String STATISTICAL_CODE_TYPE_ID_KEY = "statisticalCodeTypeId";

  public StatisticalCode(String name, String code, String statisticalCodeTypeId, String source) {
    super.setProperty(NAME_KEY, name);
    super.setProperty(CODE_KEY, code);
    super.setProperty(STATISTICAL_CODE_TYPE_ID_KEY, statisticalCodeTypeId);
    super.setProperty(SOURCE_KEY, source);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }
}
