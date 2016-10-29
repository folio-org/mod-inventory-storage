package org.folio.inventory_storage.app

// import com.test.vertx.utils.Runner;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableAutoConfiguration
public class Deployer extends AbstractVerticle {

    public static ApplicationContext ctx;

    // @Autowired
    // DummyService dummyService = DummyService.dummyService;

    public static void main(String[] args) {
        ctx = SpringApplication.run(Deployer.class, args);
        SpringApplicationContextHolder.setAppContext(ctx);
        // Runner.runVertile(Deployer.class);
    }

    @Bean
    @Override
    public Vertx getVertx() {
        return this.vertx;
    }

    // @Bean
    // Verticle serverFactory() {
    //     return new Server();
    // }

    @Override
    public void start() throws Exception {
    //     for (int i=0; i<10; i++) {
    //         vertx.deployVerticle(serverFactory(), new DeploymentOptions());
    //     }
    }

    // @RequestMapping("/")
    // public String landing () {
    //     return "This is the Spring Part calling " + dummyService.dummyMethod();
    // }
}
