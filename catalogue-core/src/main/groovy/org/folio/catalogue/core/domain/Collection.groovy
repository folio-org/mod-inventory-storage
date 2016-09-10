package org.folio.catalogue.core.domain

interface Collection<T> {
  void empty()

  T add(T item)

  List<T> findAll()

  T findById(String id)

  void add(T item, Closure resultCallback)

  void findById(String id, Closure resultCallback)

  void findAll(Closure resultCallback)
}