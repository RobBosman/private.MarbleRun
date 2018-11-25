package nl.bransom.marblerun

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.rxjava.core.AbstractVerticle
import io.vertx.rxjava.ext.web.Router
import io.vertx.rxjava.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import rx.Single
import java.util.concurrent.TimeUnit

/**
 * Send a GET request to http://localhost:8081/ to trigger this microservice.
 * It will invoke the HelloMicroservice twice and combine the responses.
 */
class HelloConsumerMicroservice : AbstractVerticle() {

  private val LOG = LoggerFactory.getLogger(javaClass)
  private val HOST = "localhost"
  private val PORT = 8081

  override fun start(result: Future<Void>) {
    val router = Router.router(vertx)

    router.get("/").handler { routingContext -> invokeService(routingContext) }

    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(PORT) { startResult ->
          if (startResult.succeeded()) {
            LOG.info("Listening on http://{}:{}/", HOST, PORT)
            result.complete()
          } else {
            result.fail(startResult.cause())
          }
        }
  }

  private fun invokeService(routingContext: RoutingContext) {
    LOG.debug("Processing HTTP request")

    // Send the request messages...
    val lukeSingle = rxSendMessage("Luke")
    val leiaSingle = rxSendMessage("Leia")

    // ...and combine the responses
    Single.zip(
        lukeSingle,
        leiaSingle,
        { lukeResponse, leiaResponse ->
          JsonObject()
              .put("luke", lukeResponse)
              .put("leia", leiaResponse)
        })
        .timeout(100, TimeUnit.MILLISECONDS)
        .retry(9)
        .map(JsonObject::encodePrettily)
        .subscribe(
            { message -> routingContext.response().end(message) },
            { throwable ->
              routingContext.response()
                  .setStatusCode(500)
                  .end(throwable.message)
            })
  }

  private fun rxSendMessage(body: String): Single<String> {
    return vertx
        .eventBus()
        .rxSend<JsonObject>(ADDRESS, body)
        .map { message -> message.body() }
        .map { json -> "${json.getString(MESSAGE_KEY)} from ${json.getString(SERVED_BY_KEY)} at ${json.getString(AT_KEY)}" }
  }
}
