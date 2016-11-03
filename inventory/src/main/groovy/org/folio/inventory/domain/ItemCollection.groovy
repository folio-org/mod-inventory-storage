package org.folio.inventory.domain

import org.folio.metadata.common.domain.BatchCollection
import org.folio.metadata.common.domain.Collection

interface ItemCollection extends Collection<Item>,
  BatchCollection<Item> {

  def findByTitle(String partialTitle, Closure completionCallback)
}
