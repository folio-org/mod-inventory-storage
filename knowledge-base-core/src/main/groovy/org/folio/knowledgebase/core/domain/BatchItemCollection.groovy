package org.folio.knowledgebase.core.domain

interface BatchItemCollection<T> {
  List<T> add(List<T> items)
//    void add(List<T> collection, Closure resultCallback)
}
