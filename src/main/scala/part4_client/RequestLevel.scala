package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import part4_client.PaymentSystemDomain._
import spray.json._

import scala.concurrent.Future

object RequestLevel extends App with PaymentJsonProtocol {

    /**
     * Request level API benefits:-
     * The freedom for managing anything.
     * Dead simple.
     */
    implicit val system: ActorSystem = ActorSystem("RequestLevel")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri="http://www.google.co.in"))

    /*responseFuture.onComplete {
        case Success(response) =>
            response.discardEntityBytes()//important
            println(s"Success on request with response ${response.status}")
        case Failure(ex) => println(s"Failed request with: ${ex.getMessage}")
    }*/

    val creditCards = List(
        CreditCard("4242-4242-4242-4242", "321", "tx-test-account"),
        CreditCard("5656-5656-5757-5757", "535", "my-base-account"),
        CreditCard("1235-1234-1235-1234", "123", "tx-nah-account")
    )

    val paymentRequest = creditCards.map(creditCard => PaymentRequest(creditCard, "kas-store-account", 500))

    val serverHttpRequest = paymentRequest.map { paymentRequest =>
        HttpRequest(
            HttpMethods.POST,
            uri = "http://localhost:8080/api/payments",//absolute uri
            entity = HttpEntity(
                ContentTypes.`application/json`,
                paymentRequest.toJson.prettyPrint
            )
        )
    }

    // turn list of request to source
    Source(serverHttpRequest)
        .mapAsync(10)(request => Http().singleRequest(request))
        // parallelism->no.of thread, and function from HttpRequest => Future[ourType]
        .runForeach(println)

    /*First run the server(PaymentSystem.scala) then client(RequestLevel.scala)*/

}
