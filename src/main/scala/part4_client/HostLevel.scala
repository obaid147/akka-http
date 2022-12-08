package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import part4_client.PaymentSystemDomain._
import spray.json._

import java.util.UUID
import scala.util.{Failure, Success, Try}

object HostLevel extends App with PaymentJsonProtocol {

    /**Benefits:-
     * 1. The freedom from managing individual connections.
     * 2. The ability to attach data to requests(aside from payloads)
     * 3. Recommended for high volume and low latency(short lived) requests*/

    implicit val system: ActorSystem = ActorSystem("HostLevel")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val poolFlow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Http.HostConnectionPool] =
        Http().cachedHostConnectionPool[Int]("www.google.co.in")
        /*connections are stored in cache pool and reused.
        * HttpRequests we send through this pool, will go through any of the available connections and
        * we may receive HttpResponse in different order then the original order of request.
        * This API allows us association between httpReq&Value.
        * When we receive response, the same value will be attached with response associated with original request.
        * */
    val sourceAndFlow = Source(1 to 10)
        .map(i => (HttpRequest(), i))
        .via(poolFlow)

    sourceAndFlow.map {
        case (Success(response), value) =>
            response.discardEntityBytes()
            // if we didn't discard, It will block the connection that this "response" wants to get through.
            // We don't want leaking connection or overflow them.
            s"request $value has received ${response.status};${response.protocol}\n"
        case (Failure(ex), value) =>
            s"request $value has failed $ex"
    }
        //.runWith(Sink.foreach[String](println)) // run the app with & without response.discardEntityBytes()


    val creditCards = List(
        CreditCard("4242-4242-4242-4242", "321", "tx-test-account"),
        CreditCard("5656-5656-5757-5757", "535", "my-base-account"),
        CreditCard("1235-1234-1235-1234", "123", "tx-nah-account")
    )

    val paymentRequest = creditCards.map(creditCard => PaymentRequest(creditCard, "kas-store-account", 500))

    val serverHttpRequest: List[(HttpRequest, String)] = paymentRequest.map { paymentRequest =>
        (
            HttpRequest(
            HttpMethods.POST,
            uri = Uri("/api/payments"),
            entity = HttpEntity(
                ContentTypes.`application/json`,
                paymentRequest.toJson.prettyPrint
                )
            ),
            UUID.randomUUID().toString
        )
    } // pair of HttpReq and UniqueID. We can track HttpRequest with UUID

    //send req through flow to payment microservice
    Source(serverHttpRequest)
        .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
        .runForeach{ //(Try[HttpResponse], String)
            case (Success(response@HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
                // matching StatusCodes.Forbidden
                println(s"The order id $orderId was not allowed to proceed: $response")
            case (Success(response), orderId) =>
                println(s"The order id: $orderId was successful and returned response $response")
                // do something with order id: dispatch it, send notification to customer...etc.
            case (Failure(ex), orderId) =>
                println(s"The order id: $orderId could not be completed: $ex")
        }

    // spin up the PaymentSystem.scala, then This app and make sure .run is/are commented if any.

    /**
     * it's still an anti pattern to send one-off request(limited to a single time, occasion, or instance)
     * through the host level API (use request level api).
     * And on the other hand, a consequence of using a connection pool is that long lived requests can starve
     * the pool for connections.
     * So the host level API should be used for high volume and low latency(short lived) requests.
     */
}
