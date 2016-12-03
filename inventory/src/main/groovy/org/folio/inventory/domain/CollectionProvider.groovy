package org.folio.inventory.domain

interface CollectionProvider {
  ItemCollection getItemCollection(String tenantId)
  InstanceCollection getInstanceCollection(String tenantId)
}
