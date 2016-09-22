package org.folio.metadata.common.storage.mongo

import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoClients
import com.mongodb.async.client.MongoCollection as Collection
import com.mongodb.client.model.Filters
import com.mongodb.client.result.UpdateResult
import org.bson.Document
import org.bson.types.ObjectId

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MongoCollection<T> {

  private final TimeUnit timeoutUnit = TimeUnit.MILLISECONDS
  private final int timeoutDuration = 1000

  final Collection<Document> collection

  private final Closure fromDocument
  private final Closure toMap

  def MongoCollection(
    String databaseName,
    String collectionName,
    Closure fromDocument,
    Closure toMap) {
    this.fromDocument = fromDocument
    this.toMap = toMap

    collection = MongoClients.create()
      .getDatabase(databaseName)
      .getCollection(collectionName)
  }

  T add(T item) {
    CompletableFuture<T> future = new CompletableFuture<T>()

    add(item, { future.complete(it) })

    future.get(timeoutDuration, timeoutUnit)
  }

  void add(T item, Closure resultCallback) {
    Document document = new Document(toMap(item))

    SingleResultCallback<Void> callback = new SingleResultCallback<Void>() {
      @Override
      public void onResult(final Void result, final Throwable t) {
        T insertedItem = fromDocument(document)

        resultCallback(insertedItem)
      }
    }

    collection.insertOne(document, callback)
  }

  T add(List<T> items) {
    //TODO: Replace with proper batching
    items.collect({ it -> add(it) })
  }

  List<T> find(filter) {
    CompletableFuture<T> future = new CompletableFuture<T>()

    find(filter, { future.complete(it) })

    future.get(timeoutDuration, timeoutUnit)
  }

  void find(filter, resultCallback) {
    SingleResultCallback<Document> callback = new SingleResultCallback<List<Document>>() {
      @Override
      public void onResult(final List<Document> documents, final Throwable t) {
        resultCallback(documents.collect({ fromDocument(it) }))
      }
    }

    collection.find(filter).into(new ArrayList<Document>(), callback)
  }

  T findOne(filter) {
    CompletableFuture<T> future = new CompletableFuture<T>()

    findOne(filter, { future.complete(it) })

    future.get(timeoutDuration, timeoutUnit)
  }

  void findOne(filter, Closure resultCallback) {
    SingleResultCallback<Document> callback = new SingleResultCallback<Document>() {
      @Override
      public void onResult(final Document document, final Throwable t) {
        resultCallback(fromDocument(document))
      }
    }

    collection.find(filter).first(callback)
  }

  T findById(id) {
    CompletableFuture<T> future = new CompletableFuture<T>()

    findById(id, { future.complete(it) })

    future.get(timeoutDuration, timeoutUnit)
  }

  void findById(id, Closure resultCallback) {
    findOne(Filters.eq("_id", new ObjectId(id)), resultCallback)
  }

  List<T> findAll() {
    CompletableFuture<T> future = new CompletableFuture<T>()

    findAll({ future.complete(it) })

    future.get(timeoutDuration, timeoutUnit)
  }

  void findAll(Closure resultCallback) {
    SingleResultCallback<Document> callback = new SingleResultCallback<List<Document>>() {
      @Override
      public void onResult(final List<Document> documents, final Throwable t) {

        def collect = documents.collect({ fromDocument(it) })

        resultCallback(collect)
      }
    }

    collection.find().into(new ArrayList<Document>(), callback)
  }

  void update(id, updates) {
    CompletableFuture<Void> future = new CompletableFuture<Void>()

    update(id, updates, { future.complete() })

    future.get(timeoutDuration, timeoutUnit)
  }

  void update(id, updates, Closure completionCallback) {
    SingleResultCallback<UpdateResult> callback = new SingleResultCallback<UpdateResult>() {
      @Override
      public void onResult(final UpdateResult updateResult, final Throwable t) {
        if (updateResult.modifiedCount == 1) {
          completionCallback()
        }
      }
    }

    collection.updateOne(
      Filters.eq("_id", new ObjectId(id)),
      updates, callback)
  }

  void empty() {
    CompletableFuture<Void> future = new CompletableFuture<Void>()

    SingleResultCallback<Void> callback = new SingleResultCallback<Void>() {
      @Override
      public void onResult(final Void result, final Throwable t) {
        future.complete()
      }
    }

    collection.drop(callback)

    future.get(timeoutDuration, timeoutUnit)
  }
}
