package org.folio.catalogue.core.domain

import org.folio.metadata.common.domain.AsynchronousCollection
import org.folio.metadata.common.domain.BatchCollection
import org.folio.metadata.common.domain.SynchronousCollection

interface ItemCollection extends AsynchronousCollection<Item>, SynchronousCollection<Item>,
  BatchCollection<Item> {

  def findByTitle(String partialTitle, Closure completionCallback)
}
