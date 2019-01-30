/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api.entities;

/**
 *
 * @author ne
 */
public class InstanceRelationship extends JsonEntity {
   // JSON property names
  public static final String SUPER_INSTANCE_ID_KEY = "superInstanceId";
  public static final String SUB_INSTANCE_ID_KEY = "subInstanceId";
  public static final String INSTANCE_RELATIONSHIP_TYPE_ID_KEY = "instanceRelationshipTypeId";

  public InstanceRelationship() {}

  public InstanceRelationship(String superInstanceId, String subInstanceId, String instanceRelationshipTypeId) {
    super.setProperty(SUPER_INSTANCE_ID_KEY, superInstanceId);
    super.setProperty(SUB_INSTANCE_ID_KEY, subInstanceId);
    super.setProperty(INSTANCE_RELATIONSHIP_TYPE_ID_KEY, instanceRelationshipTypeId);
  }

  @Override
  public InstanceRelationship put (String key, Object value) {
    setProperty(key, value);
    return this;
  }
}
