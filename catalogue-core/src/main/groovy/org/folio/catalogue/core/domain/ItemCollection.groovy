package org.folio.catalogue.core.domain

import org.folio.metadata.common.domain.Collection
import org.folio.metadata.common.domain.BatchCollection

interface ItemCollection extends Collection<Item>,
  BatchCollection<Item> {

  def findByTitle(String partialTitle, Closure completionCallback)
}
