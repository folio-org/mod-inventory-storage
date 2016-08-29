package catalogue.core.api.representation

import catalogue.core.domain.Item
import io.vertx.groovy.core.http.HttpServerRequest
import catalogue.core.api.ResourceMap

class ItemRepresentation {
    static toMap(Item item, HttpServerRequest request) {
        def representation = [:]

        representation.title = item.title
        representation.links = ['self' : ResourceMap.itemAbsolute("/${item.id}", request)]

        representation
    }
}
