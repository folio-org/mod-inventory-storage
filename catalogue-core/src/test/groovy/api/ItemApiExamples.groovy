package api

import catalogue.core.domain.Item
import catalogue.core.storage.Storage
import spock.lang.Specification
import support.HttpClient
import support.World

class ItemApiExamples extends Specification {

    def setupSpec() {
        World.startApi()
    }

    def cleanupSpec() {
        World.stopApi()
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

        def firstItem = new Item("A Long Way to a Small Angry Planet")

        def secondItem = new Item("Nod")

        itemCollection.add([firstItem, secondItem])
    }

    private void selfLinkShouldBeReachable(instance) {
        assert HttpClient.canGet(instance.links.self)
    }

    private void selfLinkShouldRespectWayResourceWasReached(instance) {
        assert instance.links.self.contains(World.apiRoot().toString())
    }
}
