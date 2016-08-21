package api

import io.vertx.core.json.JsonObject
import knowledgebase.core.domain.Instance
import knowledgebase.core.storage.Storage
import spock.lang.Specification
import support.HttpClient
import support.World

class InstanceApiExamples extends Specification {

    def setupSpec() {
        World.startApi()
    }

    def cleanupSpec() {
        World.stopApi()
    }

    void "Create a new instance"() {
        given:
            World.reset()

        when:
            JsonObject newInstanceRequest = new JsonObject()
                .put("title", "The End of the World Running Club")
                .put("identifiers", [[namespace:"asin", value:"B00LD69QGO"],
                                     [namespace:"isbn", value:"1785032666"]]);

            def locationOfNewInstance = HttpClient.postToCreate(World.instanceApiRoot(),
                    newInstanceRequest.encodePrettily())

            def newInstance = HttpClient.get(locationOfNewInstance)
        then:
            assert newInstance.title == "The End of the World Running Club"
            assert newInstance.identifiers[0].namespace == "asin"
            assert newInstance.identifiers[0].value == "B00LD69QGO"
            assert newInstance.identifiers[1].namespace == "isbn"
            assert newInstance.identifiers[1].value == "1785032666"

            assert newInstance.links.self.contains(World.apiRoot().toString())
            assert HttpClient.canGet(newInstance.links.self)
    }

    void "Find all instances and navigate to them"() {
        given:
            World.reset()
            def instances = someInstances()

        when:
            def instancesFromApi = findAllInstances()

        then:
            assert instancesFromApi.size() == 2
            def firstInstance = instancesFromApi[0]

            assert firstInstance.title == "A Long Way to a Small Angry Planet"
            assert firstInstance.links.self == "${World.instanceApiRoot()}/${instances[0].id}"

            assert firstInstance.identifiers[0].namespace == 'isbn'
            assert firstInstance.identifiers[0].value == '9781473619777'

            assert firstInstance.links.self.contains(World.apiRoot().toString())
            assert HttpClient.canGet(firstInstance.links.self)

            def secondInstance = instancesFromApi[1]
    
            assert secondInstance.title == "Nod"
            assert secondInstance.links.self == "${World.instanceApiRoot()}/${instances[1].id}"
    
            assert secondInstance.identifiers[0].namespace == 'asin'
            assert secondInstance.identifiers[0].value == 'B01D1PLMDO'
    
            assert secondInstance.links.self.contains(World.apiRoot().toString())
            assert HttpClient.canGet(secondInstance.links.self)
    }

    void "Find by partial title"() {
        given:
            World.reset()
            someInstances()

        when:
            def instancesFromApi = findInstancesByPartialTitle("Long Way")

        then:
            assert instancesFromApi.size() == 1
            def firstInstance = instancesFromApi[0]

            assert firstInstance.title == "A Long Way to a Small Angry Planet"

            assert HttpClient.canGet(firstInstance.links.self)
    }

    void "Find by isbn identifier"() {
        given:
            World.reset()
            someInstances()

        when:
            def instancesFromApi = findInstanceByIdentifier('isbn', '9781473619777')

        then:
            assert instancesFromApi.size() == 1
            def firstInstance = instancesFromApi[0]

            assert firstInstance.title == "A Long Way to a Small Angry Planet"

            assert HttpClient.canGet(firstInstance.links.self)
    }

    void "Cannot find an unknown resource"() {
        given:
            World.reset()

        when:
            def status;
            HttpClient.getExpectingFailure(World.instanceApiRoot().toString() + "/${UUID.randomUUID()}",
                    { resp, body ->  status = resp.statusLine })

        then:
            assert status.toString().contains("404")
    }

    private static findAllInstances() {
        HttpClient.get(World.instanceApiRoot())
    }

    private static someInstances() {
        def instanceCollection = Storage.collectionProvider.instanceCollection

        def firstInstance = new Instance("A Long Way to a Small Angry Planet")

        firstInstance = firstInstance.addIdentifier('isbn', '9781473619777')

        def secondInstance = new Instance("Nod")

        secondInstance = secondInstance.addIdentifier('asin', 'B01D1PLMDO')

        instanceCollection.add([firstInstance, secondInstance])
    }

    private def findInstancesByPartialTitle(String partialTitle) {
        HttpClient.getByQuery(World.instanceApiRoot(), [partialTitle: partialTitle])
    }

    def findInstanceByIdentifier(String namespace, String value) {
        HttpClient.getByQuery(World.instanceApiRoot(), [(namespace): value])
    }
}
