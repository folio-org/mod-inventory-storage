package org.folio.inventory.domain

import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.AsynchronousCollection

interface ItemCollection extends AsynchronousCollection<Item> {
  void findByCql(String cqlQuery, PagingParameters pagingParameters,
                 Closure resultCallback)
}
