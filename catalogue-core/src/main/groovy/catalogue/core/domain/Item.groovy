package catalogue.core.domain

class Item {

    final String id
    final String title
    final String instanceLocation
    final String barcode

    def Item(String id, String title, String instanceLocation, String barcode) {
        this.id = id
        this.title = title
        this.instanceLocation = instanceLocation
        this.barcode = barcode
    }

    def Item(String title, String instanceLocation, String barcode) {
        this(null, title, instanceLocation, barcode)
    }

    def Item copyWithNewId(String id) {
        new Item(id, title, instanceLocation, barcode)
    }

    Map toMap() {
        def instanceMap = [:]
        instanceMap.id = this.id
        instanceMap.title = this.title
        instanceMap.instanceLocation = this.instanceLocation
        instanceMap.barcode = this.barcode
        instanceMap
    }

    @Override
    def String toString() {
        String.format("Id: %s, Title: %s", this.id, this.title)
    }
}
