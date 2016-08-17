package knowledgebase.core

import io.vertx.groovy.core.Vertx

public class Launcher {
  public static void main(String[] args) {

    println "Server Starting"

    ApiVerticle.deploy(Vertx.vertx())

    println "Server Started"
  }
}
