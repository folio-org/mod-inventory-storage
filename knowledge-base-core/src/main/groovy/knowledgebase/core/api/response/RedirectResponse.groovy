package knowledgebase.core.api.response

import io.vertx.groovy.core.http.HttpServerResponse

class RedirectResponse {
  static redirect(HttpServerResponse response, String url) {
    response.headers().add("Location", url)
    response.setStatusCode(303)
    response.end()
  }

  static created(HttpServerResponse response, String url) {
    response.headers().add("Location", url)
    response.setStatusCode(201)
    response.end()
  }
}
