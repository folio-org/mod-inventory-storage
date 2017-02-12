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

  void some(PagingParameters pagingParameters,
            Consumer<Success> resultCallback) {

    resultCallback.accept(new Success(all().stream()
      .skip(pagingParameters.offset)
      .limit(pagingParameters.limit)
      .collect()))
  }

  T findOne(Closure matcher) {
    items.find(matcher)
  }

  void findOne(Closure matcher, Consumer<Success<T>> successCallback) {
    successCallback.accept(new Success(items.find(matcher)))
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

  void add(T item, Closure resultCallback) {
    items.add(item)
    resultCallback(item)
  }

  void add(T item, Consumer<Success<T>> resultCallback) {
    items.add(item)
    resultCallback.accept(new Success<T>(item))
  }

  void replace(T item, Consumer<Success> completionCallback) {
    items.removeIf({ it.id == item.id })
    items.add(item)
    completionCallback.accept(new Success(null))
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

  void empty(Consumer<Success> completionCallback) {
    items.clear()
    completionCallback.accept(new Success())
  }

  void remove(String id, Closure completionCallback) {
    items.removeIf({ it.id == id })
    completionCallback()
  }

  void remove(String id, Consumer<Success> completionCallback) {
    items.removeIf({ it.id == id })
    completionCallback.accept(new Success())
  }
}
