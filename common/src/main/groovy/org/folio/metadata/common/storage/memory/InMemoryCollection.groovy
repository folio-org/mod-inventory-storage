package org.folio.metadata.common.storage.memory

import org.folio.metadata.common.api.request.PagingParameters

//TODO: truly asynchronous implementation
class InMemoryCollection<T> {

  public final List<T> items = new ArrayList<T>()

  List<T> find(Closure matcher) {
    items.findAll(matcher)
  }

  List<T> all() {
    items.collect()
  }

  void all(Closure resultCallback) {
    resultCallback(all())
  }

  void some(PagingParameters pagingParameters, Closure resultCallback) {
    resultCallback(all().stream()
      .skip(pagingParameters.offset)
      .limit(pagingParameters.limit)
      .collect())
  }

  T findOne(Closure matcher) {
    items.find(matcher)
  }

  void findOne(Closure matcher, Closure resultCallback) {
    resultCallback(items.find(matcher))
  }

  void find(Closure matcher, Closure resultCallback) {
    resultCallback(items.findAll(matcher))
  }

  T add(T item) {
    items.add(item)
    item
  }

  void add(T item, resultCallback) {
    items.add(item)
    resultCallback(item)
  }

  void replace(T item, completionCallback) {
    items.removeIf({ it.id == item.id })
    items.add(item)
    completionCallback()
  }

  List<T> add(List<T> itemsToAdd) {
    items.addAll(itemsToAdd)
    itemsToAdd
  }

  void empty() {
    items.clear()
  }

  void empty(Closure completionCallback) {
    items.clear()
    completionCallback()
  }
}
