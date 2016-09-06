package knowledgebase.core.storage.mongo

import com.mongodb.client.model.Filters
import knowledgebase.core.domain.Instance
import knowledgebase.core.domain.InstanceCollection
import org.bson.Document
import java.util.regex.Pattern

class MongoInstanceCollection implements InstanceCollection {

  final knowledgebase.core.storage.mongo.MongoItemCollection collection

  def MongoInstanceCollection(String databaseName) {
    collection = new MongoItemCollection(databaseName, 'instance', this.&fromDoc, this.&toMap)
  }

  @Override
  void empty() {
    collection.empty()
  }

  @Override
  Instance add(Instance item) {
    collection.add(item)
  }

  @Override
  List<Instance> add(List<Instance> items) {
    collection.add(items)
  }

  @Override
  void add(Instance item, Closure resultCallback) {
    collection.add(item, resultCallback)
  }

  @Override
  void findById(String id, Closure resultCallback) {
    collection.findById(id, resultCallback)
  }

  @Override
  List<Instance> findByPartialTitle(String partialName) {
    collection.find(Filters.regex("title", java.util.regex.Pattern.compile(partialName,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)))
  }

  @Override
  void findByPartialTitle(String partialName, Closure resultCallback) {
    collection.find(Filters.regex("title", java.util.regex.Pattern.compile(partialName,
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

  private static Instance fromDoc(Document document) {
    new Instance(document.get('_id').toString(),
      document.get('title'),
      document.get('identifiers'))
  }

  private static Map toMap(Instance instance) {
    instance.toMap().minus(['id': instance.id])
  }
}
