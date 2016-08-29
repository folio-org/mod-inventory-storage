package catalogue.core.domain

class Item {

    final String id
    final String title

    def Item(String id, String title) {
        this.id = id
        this.title = title
    }

    def Item(String title) {
        this(null, title)
    }

    Map toMap() {
        def instanceMap = [:]
        instanceMap.id = this.id
        instanceMap.title = this.title
        instanceMap
    }

    @Override
    def String toString() {
        String.format("Id: %s, Title: %s", this.id, this.title)
    }
}
