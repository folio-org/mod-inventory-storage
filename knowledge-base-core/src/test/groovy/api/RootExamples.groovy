package api

import spock.lang.Specification
import support.World

import static support.HttpClient.canGet
import static support.HttpClient.get

public class RootExamples extends Specification {

    def setup() {
        World.startApi()
    }

    def teardown() {
        World.stopApi()
    }

    void "Root provides a JSON response"() {
        when:
            def response = get(World.apiRoot())

        then:
            assert response.message == 'Welcome to the Folio Knowledge Base'
            assert response.links.instances.contains(World.apiRoot().toString())
            assert canGet(response.links.instances)
    }

}