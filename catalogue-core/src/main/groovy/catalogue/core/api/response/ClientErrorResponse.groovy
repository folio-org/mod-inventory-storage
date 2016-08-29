package catalogue.core.api.response

import io.vertx.groovy.core.http.HttpServerResponse

class ClientErrorResponse {
    static notFound(HttpServerResponse response) {
        response.setStatusCode(404)
        response.end()
    }
}
