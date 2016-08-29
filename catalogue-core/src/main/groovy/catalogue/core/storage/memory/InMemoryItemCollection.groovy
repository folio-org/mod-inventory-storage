package catalogue.core.storage.memory

import catalogue.core.domain.Item
import catalogue.core.domain.ItemCollection

class InMemoryItemCollection
        implements ItemCollection {

    private final collection = new InMemoryCollection<Item>()

    @Override
    List<Item> add(List<Item> itemsToAdd) {
        def addedInstances = itemsToAdd.collect() {
            new Item(UUID.randomUUID().toString(), it.title)
        }

        collection.add(addedInstances)
    }

    @Override
    void empty() {
        collection.empty()
    }

    @Override
    Item add(Item item) {
        def addedInstance = new Item(UUID.randomUUID().toString(), item.title)
        collection.add(addedInstance)
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
        def addedInstance = new Item(UUID.randomUUID().toString(), item.title)
        collection.add(addedInstance, resultCallback)
    }

    @Override
    void findById(String id, Closure resultCallback) {
        collection.findOne({ it.id == id }, resultCallback)
    }

    @Override
    void findAll(Closure resultCallback) {
        collection.all(resultCallback)
    }
}
