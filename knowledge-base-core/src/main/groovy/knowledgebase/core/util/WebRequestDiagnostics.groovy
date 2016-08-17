package knowledgebase.core.util

import io.vertx.ext.web.RoutingContext

class WebRequestDiagnostics {

    static void outputDiagnostics(RoutingContext routingContext) {

        printf "Handling %s\n", routingContext.normalisedPath()

        outputHeaders routingContext

        routingContext.next()
    }

    private static void outputHeaders(RoutingContext routingContext) {
        println "Headers"

        for (Map.Entry<String, String> entry : routingContext.request().headers().entries()) {
            printf "%s : %s\n", entry.getKey(), entry.getValue()
        }

        println()
    }
}
