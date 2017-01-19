package org.folio.knowledgebase.core.storage.mongo

import com.mongodb.client.model.Filters
import org.apache.commons.lang.NotImplementedException
import org.folio.knowledgebase.core.domain.InstanceCollection
import org.bson.Document
import org.folio.knowledgebase.core.domain.Instance
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.storage.mongo.MongoCollection

import java.util.regex.Pattern

class MongoInstanceCollection implements InstanceCollection {

  final MongoCollection collection

  def MongoInstanceCollection(String databaseName) {
    collection = new MongoCollection(databaseName, 'instance', this.&fromDoc, this.&toMap)
  }

  @Override
  void empty() {
    collection.empty()
  }

  @Override
  Instance add(Instance instance) {
    collection.add(instance)
  }

  @Override
  List<Instance> add(List<Instance> instances) {
    collection.add(instances)
  }

  @Override
  void empty(Closure completionCallback) {
    collection.empty(completionCallback)
  }

  @Override
  void add(Instance instance, Closure resultCallback) {
    collection.add(instance, resultCallback)
  }

  @Override
  void findById(String id, Closure resultCallback) {
    collection.findById(id, resultCallback)
  }

  @Override
  List<Instance> findByTitle(String partialTitle) {
    collection.find(Filters.regex("title", java.util.regex.Pattern.compile(partialTitle,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)))
  }

  @Override
  void findByTitle(String partialTitle, Closure resultCallback) {
    collection.find(Filters.regex("title", java.util.regex.Pattern.compile(partialTitle,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)), resultCallback)
  }

  @Override
  List<Instance> findByIdentifier(String namespace, String identifier) {
    collection.find(Filters.elemMatch("identifiers",
      Filters.and(
        Filters.eq('value', identifier),
        Filters.eq('namespace', namespace))))
  }

  @Override
  void findByIdentifier(String namespace, String identifier, Closure resultCallback) {
    collection.find(Filters.elemMatch("identifiers",
      Filters.and(
        Filters.eq('value', identifier),
        Filters.eq('namespace', namespace))), resultCallback)
  }

  @Override
  Instance findById(String id) {
    collection.findById(id)
  }

  @Override
  List<Instance> findAll() {
    collection.findAll()
  }

  @Override
  void findAll(Closure resultCallback) {
    collection.findAll(resultCallback)
  }

  @Override
  void findAll(PagingParameters pagingParameters, Closure resultCallback) {
    throw new NotImplementedException("Paging operations not implemented")
  }

  private static Instance fromDoc(Document document) {
    new Instance(document.get('_id').toString(),
      document.get('title'),
      document.get('identifiers'))
  }

  private static Map toMap(Instance instance) {
    instance.toMap().minus(['id': instance.id])
  }
}
