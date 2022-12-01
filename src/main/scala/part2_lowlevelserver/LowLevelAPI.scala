package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future

object LowLevelAPI extends App {

  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer() // () means we are calling apply method on that
  import system.dispatcher

  /** Creates a Source of Http.IncomingConnection instances which represents a prospective HTTP server
   * binding on the given endpoint.

   *  If the given port is 0 the resulting source can be materialized several times. Each materialization will then be
   *  assigned a new local port by the operating system, which can then be retrieved by the
   *  materialized Http.ServerBinding.

   *  If the given port is non-zero subsequent materialization attempts of the produced source will immediately fail,
   *  unless the first materialization has already been unbound. Unbinding can be triggered via the
   *  materialized Http.ServerBinding.

   *  If an ConnectionContext is given it will be used for setting up TLS encryption on the binding.
   *  Otherwise the binding will be unencrypted.

   *  If no port is explicitly given (or the port value is negative) the protocol's default port will be used,
   *  which is 80 for HTTP and 443 for HTTPS.

   *  To configure additional settings for a server started using this method, use the akka.http.server config
   *  section or pass in a ServerSettings explicitly.*/
/*

  // first server
  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] = {
    // takes an Http.IncomingConnection and returns a materialized value Future[Http.ServerBinding]
    Http().bind(interface="localhost", port=8081)
  }

  val connectionsSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from ${connection.remoteAddress}")
    // connection has the remote address that is trying to connect.
  }

  // start server
  val serverBindingFuture: Future[Http.ServerBinding] = serverSource.to(connectionsSink).run()
  serverBindingFuture.onComplete{
    case Success(binding) =>
      println("server binding successful")
      //binding.unbind()
      //unbind Asynchronously triggers the unbinding of the port that was bound by the materialization of the connections.
      binding.terminate(2.seconds)//Triggers "graceful" termination request being handled on this connection.
    case Failure(ex) => println(s"server binding failed:- $ex")
  }
*/



  /**-------------------------Response to http requests--------------------------*/

  /**Method #1*/
  /*
  * SYNCHRONOUSLY server http response.
  * As we get an http request we serve back an http response on the same thread.
  */
  val requestHandlerSynchronous: HttpRequest => HttpResponse = {
                //  method, uri, header, entity/content, protocol
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse( // If the request was GET method then response will be 200 Ok
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity( // Payload of response will contain html
          ContentTypes.`text/html(UTF-8)`, // and actual content is multiline string.
          """
            |<html>
            |<body>
            |<h2><center>Hello from akka http</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,//404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<br><br>
            |<h2><center>OOPS! The resource cannot be found</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
  }// Function that turns HttpRequest into an HttpResponse

  // we serve http responses back immediately on the same thread
  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection]{
    connection =>
      connection.handleWithSyncHandler(requestHandlerSynchronous)
  }

  /*Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
  //source of incoming connections-------------.runWith(sink of incoming connection_*/

  //shorthand version of above
  //Http().bindAndHandleSync(requestHandlerSynchronous, "localhost", 8080)


  /**Method #2*/
  /*
  * Serve back HTTP response ASYNCHRONOUSLY.
  * We need to return Future of Http response.
  */

  val requestHandlerAsynchronous: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // Any request that are hitting /home will get OK.
      Future{
        HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              |<body>
              |<br><br>
              |<h2><center>Hello from akka <u>async</u> http</center></h2>
              |</body>
              |</html>
              |""".stripMargin
          )
        )
      }
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future{
        HttpResponse(
          StatusCodes.NotFound, //404
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              |<body>
              |<br><br>
              |<h2><center>OOPS! The resource cannot be found</center></h2>
              |</body>
              |</html>
              |""".stripMargin
          )
        )
      }
  }

  /*val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(requestHandlerAsynchronous)
  }

  Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler) // streams based version*/

  // shortHand Version
  Http().bindAndHandleAsync(requestHandlerAsynchronous, "localhost", 8081)// passing fun() directly here.


  /** Method #3 */
  /*Asynchronous via akka streams*/
  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/streams"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<br><br>
            |<h2><center>Working with akka HTTP based on akka Streams</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<br><br>
            |<h2><center>OOPS! The resource cannot be found</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
  }

  /* manual version
  //source of incoming connection--------------.runForeach
  Http().bind("localhost", 8082).runForeach { connection =>
    connection.handleWith(streamsBasedRequestHandler)
  }*/

  // shorthand version
  //Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)

  /** Create your own HTTP Server running on localhost On PORT 8388 eight thousand three hundred and eighty eight, which.
   * Replies with the following, let's say, with a welcome message om localhost:8388.
   * proper about page.
   * 404 otherwise.
   * */

  val exerciseRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        //StatusCodes.OK, is default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<br><br>
            |<h2><center>Welcome to Home Page</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      HttpResponse(
        //StatusCodes.OK, StatusCode.OK (200) is default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<br><br>
            |<div style="color: red">
            | <h2><center>Abouts page</center></h2>
            |</div>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("https://www.google.co.in"))
        /*inspect in browser -> hit http://localhost:8388/search -> Network -> Name = search with 302
        * And the header under Response Headers ie:- "Location" that we manually mentioned above*/
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<br><br>
            |<h2><center>OOPS! The resource cannot be found</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val bindingFuture = Http().bindAndHandle(exerciseRequestHandler, "localhost", 8388)

  /*bindingFuture.flatMap(binding => binding.unbind()) // unbind() returns the future.
  .onComplete(_ => system.terminate())*/ // uncomment to shutdown the akka-http server when future is complete.
}
