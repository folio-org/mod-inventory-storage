package org.folio.inventory.domain

import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.AsynchronousCollection

interface InstanceCollection extends AsynchronousCollection<Instance> {
  def findByTitle(String partialTitle, Closure completionCallback)
  void findAll(PagingParameters pagingParameters, Closure resultCallback)
}
