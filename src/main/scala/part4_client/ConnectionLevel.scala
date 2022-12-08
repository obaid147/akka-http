package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import part4_client.PaymentSystemDomain._
import spray.json._

import scala.concurrent.Future

object ConnectionLevel extends App with PaymentJsonProtocol {
    implicit val system: ActorSystem = ActorSystem("ConnectionLevel")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    /* use akka-http to issue request to other servers.
    our akka-http code will act as a client*/

    val connectionFlow = Http().outgoingConnection("www.google.co.in") // #open a connection
    /*val connectionFlow = Http().outgoingConnectionHttps("www.google.co.in",  HttpsContext.httpsConnectionContext)*/

    /**
    * source emits a single element request of type HttpRequest
    * source is connected to flow that takes HttpRequest and returns HttpResponse and
    * feeds that to a sink extracting a single element.
    * This stream is materialized as we are using runWith.
    */
    def oneOffRequest(request: HttpRequest): Future[HttpResponse] = {
        /*val src: Source[HttpRequest, NotUsed] = Source.single(request)
        val snk: Future[HttpRequest] = src.runWith(Sink.head)*/
        Source.single(request).via(connectionFlow).runWith(Sink.head)
        // #send requests through this connection and link it with Source and consumer.
    }
    /** not recommended because this method materializes the akka-stream every time and starts TCP conn every single time.*/
    /*oneOffRequest(HttpRequest()).onComplete {
        case Success(response) => println(s"Got successful response: $response")
        case Failure(ex) => println(s"Sending request failed: $ex")
    }*/

    /*
    * A small payment system
    * */

    val creditCards = List(
        CreditCard("4242-4242-4242-4242", "321", "tx-test-account"),
        CreditCard("5656-5656-5757-5757", "535", "my-base-account"),
        CreditCard("1235-1234-1235-1234", "123", "tx-nah-account")
    )

    val paymentRequest = creditCards.map(creditCard => PaymentRequest(creditCard, "kas-store-account", 500))

    val serverHttpRequest = paymentRequest.map { paymentRequest =>
        HttpRequest(
            HttpMethods.POST,
            uri = Uri("/api/payments"),
            entity = HttpEntity(
                ContentTypes.`application/json`,
                paymentRequest.toJson.prettyPrint
            )
        )
    }

    Source(serverHttpRequest).via(Http().outgoingConnection("localhost", 8080))
        .to(Sink.foreach[HttpResponse](println))
        .run()
    /*First run the server(PaymentSystem.scala) then client(ConnectionLevel.scala)*/
}
