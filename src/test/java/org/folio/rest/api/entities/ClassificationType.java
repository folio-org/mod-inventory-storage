/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.folio.rest.api.entities;


public class ClassificationType extends JsonEntity {
  public static final String NAME_KEY = "name";

  public ClassificationType(String name) {
    super.setProperty(NAME_KEY, name);
  }

  @Override
  public JsonEntity put(String propertyKey, Object value) {
    setProperty(propertyKey, value);
    return this;
  }

}
