package org.folio.inventory.domain

import org.folio.metadata.common.api.request.PagingParameters

interface SearchableCollection<T> {
  void findByCql(String cqlQuery,
                 PagingParameters pagingParameters,
                 Closure resultCallback)
}
