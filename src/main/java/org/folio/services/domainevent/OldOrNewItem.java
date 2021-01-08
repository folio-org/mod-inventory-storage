package org.folio.services.domainevent;

import static io.vertx.core.json.Json.decodeValue;
import static io.vertx.core.json.Json.encode;

import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

public class OldOrNewItem extends Item {
  private String instanceId;

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public OldOrNewItem withInstanceId(String instanceId) {
    this.instanceId = instanceId;
    return this;
  }

  public static OldOrNewItem fromItem(Item item) {
    return decodeValue(encode(item), OldOrNewItem.class);
  }

  public static OldOrNewItem fromItem(Item item, HoldingsRecord hr) {
    return fromItem(item).withInstanceId(hr.getInstanceId());
  }
}
