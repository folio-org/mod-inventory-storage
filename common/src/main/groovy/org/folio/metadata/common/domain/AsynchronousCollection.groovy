package org.folio.metadata.common.domain

interface AsynchronousCollection<T> {
  void add(T item, Closure resultCallback)

  void findById(String id, Closure resultCallback)

  void findAll(Closure resultCallback)
}
