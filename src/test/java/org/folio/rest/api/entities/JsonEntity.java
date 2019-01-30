/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api.entities;

import io.vertx.core.json.JsonObject;

/**
 *
 * @author ne
 */
public abstract class JsonEntity {

  public static final String ID_KEY = "id";

  protected final JsonObject json = new JsonObject();

  /**
   * Sets JSON property
   * (hint: override in subclass with method that returns the specific subclass instead)
   * @param propertyKey JSON property name
   * @param value JSON value (ie String, JSON object, JSON array)
   * @return Wrapped JSON representation of the entity
   */
  public abstract JsonEntity put(String propertyKey,  Object value);

  protected void setProperty (String propertyKey, Object value) {
    json.put(propertyKey, value);
  }

  public JsonObject getJson() {
    return json;
  }

}
