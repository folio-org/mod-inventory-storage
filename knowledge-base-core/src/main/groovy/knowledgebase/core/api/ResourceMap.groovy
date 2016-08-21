package knowledgebase.core.api

import io.vertx.groovy.core.http.HttpServerRequest
import knowledgebase.core.Config

class ResourceMap {

    static String root() {
        "/knowledge-base"
    }

    static String instance() {
        "/knowledge-base/instance"
    }

    static String instance(String path) {
        "/knowledge-base/instance" + path
    }

    static String instanceAbsolute(String path, HttpServerRequest request) {
        def okapiLocation = request.getHeader("X-Okapi-Url");

        (okapiLocation ?: Config.apiBaseAddress) + "knowledge-base/instance" + path
    }
}
