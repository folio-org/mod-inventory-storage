package catalogue.core.domain

class Item {

    final String id
    final String title
    final String instanceLocation

    def Item(String id, String title, String instanceLocation) {
        this.id = id
        this.title = title
        this.instanceLocation = instanceLocation
    }

    def Item(String title, String instanceLocation) {
        this(null, title, instanceLocation)
    }

    Map toMap() {
        def instanceMap = [:]
        instanceMap.id = this.id
        instanceMap.title = this.title
        instanceMap.instanceLocation = this.instanceLocation
        instanceMap
    }

    @Override
    def String toString() {
        String.format("Id: %s, Title: %s", this.id, this.title)
    }
}
