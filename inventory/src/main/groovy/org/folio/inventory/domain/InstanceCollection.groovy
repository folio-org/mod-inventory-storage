package org.folio.inventory.domain

import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.AsynchronousCollection

interface InstanceCollection extends AsynchronousCollection<Instance> {
  void findByCql(String cqlQuery, PagingParameters pagingParameters,
                Closure resultCallback)

  void update(Instance instance, Closure completionCallback, Closure failureCallback)
  void delete(String id, Closure completionCallback)
}
