package knowledgebase.core

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.groovy.core.Vertx
import knowledgebase.core.util.WebRequestDiagnostics

import java.util.concurrent.CompletableFuture

public class ApiVerticle extends AbstractVerticle {

    private HttpServer server;

    public static void deploy(Vertx vertx, CompletableFuture deployed) {
        vertx.deployVerticle("knowledgebase.core.ApiVerticle", { res ->
            if (res.succeeded()) {
                deployed.complete(null);
            } else {
                deployed.completeExceptionally(res.cause());
            }
        });
    }

    public static void deploy(Vertx vertx) {
        def deployed = new CompletableFuture()

        deploy(vertx, deployed)

        deployed.join()
    }

    @Override
    public void start(Future deployed) {
        server = vertx.createHttpServer()

        def router = Router.router(vertx)

        router.route().handler(WebRequestDiagnostics.&outputDiagnostics)

        RootResource.register(router)

        server.requestHandler(router.&accept)
                .listen(9401,
                { result ->
                    if (result.succeeded()) {
                        printf "Listening on %s \n", server.actualPort()
                        deployed.complete();
                    } else {
                        deployed.fail(result.cause());
                    }
                })
    }

    @Override
    public void stop() {
        server.close();
    }
}
