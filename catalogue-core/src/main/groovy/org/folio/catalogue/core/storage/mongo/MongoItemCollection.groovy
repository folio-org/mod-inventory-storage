package org.folio.catalogue.core.storage.mongo

import com.mongodb.client.model.Filters
import org.bson.Document
import org.folio.catalogue.core.domain.Item
import org.folio.catalogue.core.domain.ItemCollection
import org.folio.metadata.common.storage.mongo.MongoCollection

import java.util.regex.Pattern

class MongoItemCollection implements ItemCollection {

  final MongoCollection collection

  def MongoItemCollection(String databaseName) {
    collection = new MongoCollection(databaseName, 'item', this.&fromDoc, this.&toMap)
  }


  @Override
  void empty() {
    collection.empty()
  }

  @Override
  Item add(Item item) {
    collection.add(item)
  }

  @Override
  List<Item> findAll() {
    collection.findAll()
  }

  @Override
  Item findById(String id) {
    collection.findById(id)
  }

  @Override
  void add(Item item, Closure resultCallback) {
    collection.add(item, resultCallback)
  }

  @Override
  void findById(String id, Closure resultCallback) {
    collection.findById(id, resultCallback)
  }

  @Override
  void findAll(Closure resultCallback) {
    collection.findAll(resultCallback)
  }

  @Override
  List<Item> add(List<Item> items) {
    collection.add(items)
  }

  @Override
  def findByTitle(String partialTitle, Closure completionCallback) {
    collection.find(Filters.regex("title", java.util.regex.Pattern.compile(partialTitle,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)), completionCallback)
  }

  private static Item fromDoc(Document document) {
    new Item(document.get('_id').toString(),
      document.get('title'),
      document.get('instanceLocation'),
      document.get('barcode'))
  }

  private static Map toMap(Item item) {
    item.toMap().minus(['id': item.id])
  }
}
