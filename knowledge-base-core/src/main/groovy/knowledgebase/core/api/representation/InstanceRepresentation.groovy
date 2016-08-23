package knowledgebase.core.api.representation

import io.vertx.groovy.core.http.HttpServerRequest
import knowledgebase.core.api.ResourceMap
import knowledgebase.core.domain.Instance

class InstanceRepresentation {
    static toMap(Instance instance, HttpServerRequest request) {
        def representation = [:]

        representation."@context" = [
                "dcterms": "http://purl.org/dc/terms/",
                "title": "dcterms:title"
        ]

        representation.title = instance.title
        representation.links = ['self' : ResourceMap.instanceAbsolute("/${instance.id}", request)]

        representation.identifiers = []

        instance.identifiers.each { identifier ->
            def identifierRepresentation = [:]

            identifierRepresentation.namespace = identifier.namespace
            identifierRepresentation.value = identifier.value

            representation.identifiers << identifierRepresentation
        }

        representation
    }
}
