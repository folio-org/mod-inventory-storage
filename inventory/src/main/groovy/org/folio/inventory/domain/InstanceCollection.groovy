package org.folio.inventory.domain

import org.folio.metadata.common.domain.AsynchronousCollection

interface InstanceCollection extends AsynchronousCollection<Instance> {
  def findByTitle(String partialTitle, Closure completionCallback)
}
