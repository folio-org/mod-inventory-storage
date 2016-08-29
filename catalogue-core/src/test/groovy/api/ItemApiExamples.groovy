package api

import catalogue.core.domain.Item
import catalogue.core.storage.Storage
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import support.FakeKnowledgeBase
import support.HttpClient
import support.World

import java.util.concurrent.CompletableFuture

import static support.HttpClient.get

class ItemApiExamples extends Specification {

    def setupSpec() {
        def vertx = World.startVertx()

        def fakeDeployed = new CompletableFuture<Void>()

        FakeKnowledgeBase.deploy(vertx, fakeDeployed)

        World.startApi()

        fakeDeployed.join()
    }

    def cleanupSpec() {
        World.stopVertx()
    }

    void "Create a new instance"() {
        given:
            World.reset()

        when:
            JsonObject existingInstance = new JsonObject()
                .put("title", "The End of the World Running Club")
                .put("identifiers", [[namespace:"asin", value:"B00LD69QGO"],
                                     [namespace:"isbn", value:"1785032666"]]);

            def locationOfInstance = registerInstanceWithFakeKnowledgeBase(existingInstance)

            JsonObject newItemRequest = new JsonObject()
                .put("instance", locationOfInstance.toString())

            def locationOfNewItem = createNewItemViaApi(newItemRequest)

            def newItem = HttpClient.get(locationOfNewItem)
        then:
        assert newItem.title == "The End of the World Running Club"

        selfLinkShouldRespectWayResourceWasReached(newItem)
        selfLinkShouldBeReachable(newItem)

        assert newItem.links.instance == locationOfInstance.toString()
    }

    URL createNewItemViaApi(JsonObject itemRequest) {
        new URL(HttpClient.postToCreate(World.itemApiRoot(),
                itemRequest.encodePrettily()))
    }

    URL registerInstanceWithFakeKnowledgeBase(JsonObject instanceRequest) {
        new URL(HttpClient.postToCreate(GetFakeKnowledgeBaseInstanceUrl(),
                instanceRequest.encodePrettily()))
    }

    private URL GetFakeKnowledgeBaseInstanceUrl() {
        new URL(get(FakeKnowledgeBase.address).links.instances)
    }

    void "Find all instances and navigate to them"() {
        given:
            World.reset()
            someItems()

        when:
            def itemsFromApi = findAllItems()

        then:
            assert itemsFromApi.size() == 2

            def firstItem = itemsFromApi[0]
            def secondItem = itemsFromApi[1]

            assert firstItem.title == "A Long Way to a Small Angry Planet"
            assert secondItem.title == "Nod"

            selfLinkShouldRespectWayResourceWasReached(firstItem)
            selfLinkShouldRespectWayResourceWasReached(secondItem)

            selfLinkShouldBeReachable(firstItem)
            selfLinkShouldBeReachable(secondItem)
    }

    void "Cannot find an unknown item"() {
        given:
            World.reset()

        when:
            def status;
            HttpClient.getExpectingFailure(World.itemApiRoot().toString() + "/${UUID.randomUUID()}",
                    { resp, body ->  status = resp.statusLine })

        then:
            assert status.toString().contains("404")
    }

    private static findAllItems() {
        HttpClient.get(World.itemApiRoot())
    }

    private static someItems() {
        def itemCollection = Storage.itemCollection

        def firstItem = new Item("A Long Way to a Small Angry Planet", "")

        def secondItem = new Item("Nod", "")

        itemCollection.add([firstItem, secondItem])
    }

    private void selfLinkShouldBeReachable(instance) {
        assert HttpClient.canGet(instance.links.self)
    }

    private void selfLinkShouldRespectWayResourceWasReached(instance) {
        assert instance.links.self.contains(World.apiRoot().toString())
    }
}
