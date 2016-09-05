package catalogue.core

import catalogue.core.api.resource.ItemResource
import catalogue.core.storage.Storage
import io.vertx.lang.groovy.GroovyVerticle
import io.vertx.core.Future
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.core.Vertx
import catalogue.core.support.WebRequestDiagnostics
import catalogue.core.api.resource.RootResource

import java.util.concurrent.CompletableFuture

public class ApiVerticle extends GroovyVerticle {

    private HttpServer server;

    public static void deploy(Vertx vertx, CompletableFuture deployed) {
        vertx.deployVerticle("groovy:catalogue.core.ApiVerticle", { res ->
            if (res.succeeded()) {
                deployed.complete(null);
            } else {
                deployed.completeExceptionally(res.cause());
            }
        });
    }

    public static CompletableFuture<Void> deploy(Vertx vertx) {
        def deployed = new CompletableFuture()

        deploy(vertx, deployed)

        deployed
    }

    @Override
    public void start(Future started) {
        server = vertx.createHttpServer()

        def router = Router.router(vertx)

        router.route().handler(WebRequestDiagnostics.&outputDiagnostics)

        RootResource.register(router)
        ItemResource.register(router, Storage.collectionProvider.itemCollection)

        server.requestHandler(router.&accept)
                .listen(9402,
                { result ->
                    if (result.succeeded()) {
                        println "Listening on ${server.actualPort()}"
                        started.complete();
                    } else {
                        started.fail(result.cause());
                    }
                })
    }

    @Override
    public void stop(Future stopped) {
        println "Stopping catalogue API"
        server.close({ result ->
            if (result.succeeded()) {
                println "Stopped listening on ${server.actualPort()}"
                stopped.complete();
            } else {
                stopped.fail(result.cause());
            }
        });
    }
}
