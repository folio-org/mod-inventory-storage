package catalogue.core.storage.memory

import catalogue.core.domain.Item
import catalogue.core.domain.ItemCollection

import java.util.regex.Pattern

class InMemoryItemCollection
         implements ItemCollection {

    private final collection = new InMemoryCollection<Item>()

    @Override
    List<Item> add(List<Item> itemsToAdd) {
        def addedInstances = itemsToAdd.collect() {
            it.copyWithNewId(UUID.randomUUID().toString())
        }

        collection.add(addedInstances)
    }

    @Override
    void empty() {
        collection.empty()
    }

    @Override
    Item add(Item item) {
        collection.add(item.copyWithNewId(UUID.randomUUID().toString()))
    }

    @Override
    List<Item> findAll() {
        collection.all()
    }

    @Override
    Item findById(String id) {
        collection.findOne { it.id == id }
    }

    @Override
    void add(Item item, Closure resultCallback) {
        collection.add(item.copyWithNewId(UUID.randomUUID().toString()), resultCallback)
    }

    @Override
    void findById(String id, Closure resultCallback) {
        collection.findOne({ it.id == id }, resultCallback)
    }

    @Override
    void findAll(Closure resultCallback) {
        collection.all(resultCallback)
    }

    @Override
    def findByTitle(String partialTitle, Closure completionCallback) {
        return collection.find({
            Pattern.compile(
                    Pattern.quote(partialTitle),
                    Pattern.CASE_INSENSITIVE).matcher(it.title).find()
        }, completionCallback)
    }
}
