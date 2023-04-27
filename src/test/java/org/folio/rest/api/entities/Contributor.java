/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.folio.rest.api.entities;


public class Contributor extends JsonEntity {
  // JSON property names
  public static final String CONTRIBUTOR_NAME_TYPE_ID_KEY = "contributorNameTypeId";
  public static final String NAME_KEY = "name";
  public static final String CONTRIBUTOR_TYPE_ID_KEY = "contributorTypeId";
  public static final String CONTRIBUTOR_TYPE_TEXT_KEY = "contributorTypeText";

  public Contributor() { }

  public Contributor(String name, String nameTypeId) {
    super.setProperty(NAME_KEY, name);
    super.setProperty(CONTRIBUTOR_NAME_TYPE_ID_KEY, nameTypeId);
  }

  @Override
  public Contributor put(String key, Object value) {
    setProperty(key, value);
    return this;
  }
}
