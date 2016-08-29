package storage

import catalogue.core.domain.Item
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import catalogue.core.domain.ItemCollection
import catalogue.core.storage.memory.InMemoryItemCollection
import support.World

import java.util.concurrent.CompletableFuture

@RunWith(value = Parameterized.class)
class ItemCollectionExamples {

    final ItemCollection collection
    private final Item smallAngryPlanet = new Item("Long Way to a Small Angry Planet", "http://books.com/small-angry")
    private final Item nod = new Item("Nod", "http://books.com/nod")
    private final Item uprooted = new Item("Uprooted", "http://books.com/uprooted")

    public ItemCollectionExamples(ItemCollection collection) {
        this.collection = collection
    }

    @Parameterized.Parameters ( name = "{0}" )
    public static Collection data() {
        [ new InMemoryItemCollection() ]
    }

    @Before
    public void before() {
        collection.empty()
    }

    @Test
    void canBeEmptied() {
        collection.add(smallAngryPlanet)
        collection.add(nod)
        collection.add(uprooted)

        collection.empty()

        assert collection.findAll().size() == 0
    }

    @Test
    void ItemsCanBeAdded() {
        collection.add(smallAngryPlanet)
        collection.add(nod)
        collection.add(uprooted)

        def allItems = collection.findAll()

        assert allItems.size() == 3

        assert collection.findAll().every { it.id != null }

        assert allItems.any { it.title == "Long Way to a Small Angry Planet" }
        assert allItems.any { it.title == "Nod" }
        assert allItems.any { it.title == "Uprooted" }
    }

    @Test
    void instancesCanBeFoundById() {
        def addedItem = collection.add(smallAngryPlanet)
        def otherAddedItem = collection.add(nod)

        assert collection.findById(addedItem.id).title == "Long Way to a Small Angry Planet"
        assert collection.findById(addedItem.id).instanceLocation == "http://books.com/small-angry"

        assert collection.findById(otherAddedItem.id).title == "Nod"
        assert collection.findById(otherAddedItem.id).instanceLocation == "http://books.com/nod"
    }

    @Test
    void multipleItemsCanBeAddedTogether() {
        collection.add([nod, uprooted])

        def allResources = collection.findAll()

        assert allResources.size() == 2
        assert allResources.every { it.id != null }

        assert allResources.any { it.title == "Nod" }
        assert allResources.any { it.title == "Uprooted" }
    }

    @Test
    void resourcesCanBeAddedAsynchronously() {

        def firstAddFuture = new CompletableFuture<Item>()
        def secondAddFuture = new CompletableFuture<Item>()
        def thirdAddFuture = new CompletableFuture<Item>()

        collection.add(smallAngryPlanet, World.complete(firstAddFuture))
        collection.add(nod, World.complete(secondAddFuture))
        collection.add(uprooted, World.complete(thirdAddFuture))

        def allAddsFuture = CompletableFuture.allOf(firstAddFuture, secondAddFuture, thirdAddFuture)

        World.getOnCompletion(allAddsFuture)

        def findFuture = new CompletableFuture<List<Item>>()

        collection.findAll(World.complete(findFuture))

        def allResources = World.getOnCompletion(findFuture)

        assert allResources.size() == 3

        assert collection.findAll().every { it.id != null }

        assert allResources.any { it.title == "Long Way to a Small Angry Planet" }
        assert allResources.any { it.title == "Nod" }
        assert allResources.any { it.title == "Uprooted" }
    }

    @Test
    void resourcesCanBeFoundByIdAsynchronously() {
        def firstAddFuture = new CompletableFuture<Item>()
        def secondAddFuture = new CompletableFuture<Item>()

        collection.add(smallAngryPlanet, World.complete(firstAddFuture))
        collection.add(nod, World.complete(secondAddFuture))

        def addedItem = World.getOnCompletion(firstAddFuture)
        def otherAddedItem = World.getOnCompletion(secondAddFuture)

        def findAllFuture = new CompletableFuture<List<Item>>()

        collection.findAll(World.complete(findAllFuture))

        def allResources = World.getOnCompletion(findAllFuture)

        assert allResources.every { it.id != null }

        def findFuture = new CompletableFuture<Item>()
        def otherFindFuture = new CompletableFuture<Item>()

        collection.findById(addedItem.id, World.complete(findFuture))
        collection.findById(otherAddedItem.id, World.complete(otherFindFuture))

        def foundItem = World.getOnCompletion(findFuture)
        def otherFoundItem = World.getOnCompletion(otherFindFuture)

        assert foundItem.title == "Long Way to a Small Angry Planet"
        assert foundItem.instanceLocation == "http://books.com/small-angry"

        assert otherFoundItem.title == "Nod"
        assert otherFoundItem.instanceLocation == "http://books.com/nod"
    }
}