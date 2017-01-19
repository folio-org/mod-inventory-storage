package org.folio.metadata.common.domain

import org.folio.metadata.common.api.request.PagingParameters

interface AsynchronousCollection<T> {
  void empty(Closure completionCallback)
  void add(T item, Closure resultCallback)
  void findById(String id, Closure resultCallback)
  void findAll(Closure resultCallback)
  void findAll(PagingParameters pagingParameters, Closure resultCallback)
}
