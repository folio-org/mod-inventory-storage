package api

import spock.lang.Specification
import support.World

import static support.HttpClient.get

public class RootExamples extends Specification {

  def setupSpec() {
    World.startVertx()
    World.startApi()
  }

  def cleanupSpec() {
    World.stopVertx()
  }

  void "Root provides a JSON response"() {
    when:
      def response = get(World.catalogueApiRoot())

    then:
      assert response.message == 'Welcome to the Folio Catalogue Module'
  }

}
