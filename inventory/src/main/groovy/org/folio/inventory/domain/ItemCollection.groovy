package org.folio.inventory.domain

import org.folio.metadata.common.domain.AsynchronousCollection

interface ItemCollection extends AsynchronousCollection<Item> {
  //TODO: Move to AsynchronousCollection
  void empty(Closure resultCallback)
  def findByTitle(String partialTitle, Closure completionCallback)
}
