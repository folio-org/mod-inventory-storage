package org.folio.metadata.common.storage.memory

import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.cql.CqlFilter
import org.folio.metadata.common.cql.CqlParser
import org.folio.metadata.common.domain.Success

import java.util.function.Consumer

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

  void find(String cqlQuery, PagingParameters pagingParameters,
                 Closure resultCallback) {

    def (field, searchTerm) = new CqlParser().parseCql(cqlQuery)

    def filtered = all().stream()
      .filter(new CqlFilter().filterBy(field, searchTerm))
      .collect()

    def paged = filtered.stream()
      .skip(pagingParameters.offset)
      .limit(pagingParameters.limit)
      .collect()

    resultCallback(paged)
  }

  T add(T item) {
    items.add(item)
    item
  }

  void add(T item, resultCallback) {
    items.add(item)
    resultCallback(item)
  }

  void replace(T item, Consumer<Success> completionCallback) {
    items.removeIf({ it.id == item.id })
    items.add(item)
    completionCallback.accept(new Success())
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

  void remove(String id, Closure completionCallback) {
    items.removeIf({ it.id == id })
    completionCallback()
  }
}
