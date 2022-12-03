package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.language.postfixOps

object DirectivesBreakdown extends App {
  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

  /** Type1:- Filtering Directives*/
  val simpleHttpMethodRoute: Route =
    post { // get, put, patch, delete, head, options
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute: Route =
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            | About page
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    }

  val complexPathRoute: Route =
    path("api" / "myEndpoint") { // /api/myEndpoint/
      complete(StatusCodes.OK)
    }
  val donNotConfuse = {
    path("api/myEndpoint") {
      complete(StatusCodes.OK)
    }
  }
  //Http().bindAndHandle(donNotConfuse, "localhost", 8080)
  // http://localhost:8080/api/myEndpoint // 404 Not Found
  // http://localhost:8080/api%2FmyEndpoint // 200 OK , %2F is url encoding for / in "api/myEndpoint"

  //Http().bindAndHandle(complexPathRoute, "localhost", 8080)
  // http://localhost:8080/api/myEndpoint // 200 OK

  val pathEndRoute: Route =
    pathEndOrSingleSlash { // localhost:8080 OR localhost:8080/  will match both
      complete(StatusCodes.OK)
    }


  /** Type2:- Extracting Directives
   * Extracting meaningful value out of the requesting context.*/
  // example GET on  /api/itemsInStore/42   returns the item number 42, get hold of 42 to search in DB.

  val pathExtractionRoute: Route =
    path("api" / "item" / IntNumber) { (itemNumber: Int) =>
      // other directives
      println(s"Got number $itemNumber from path")
      complete(StatusCodes.OK)
    }
  //Http().bindAndHandle(pathExtractionRoute, "localhost", 8080)
  // http://localhost:8080/api/item/2  --- Terminal ==> Got number 2 from path

  val pathMultiExtractionRoute: Route =
    path("api" / "order" / IntNumber / IntNumber) { (id, inventory) =>
      println(s"Got TWO numbers $id and $inventory from path")
      complete(StatusCodes.OK)
    }
  // Http().bindAndHandle(pathMultiExtractionRoute, "localhost", 8080)

  val queryParamExtractionRoute: Route =
    path("api" / "item") {
      //      parameter("id") { itemId: String =>
      //      parameter("id".as[Int]) { itemId: Int => // make it typeSafe by casting String to Int
      parameter('id.as[Int]) { itemId: Int => // symbols using single quote (compared by ref equality means > performance)
        println(s"Extracted query parameter $itemId")
        complete(StatusCodes.OK)
      }
    }
  // .bindAndHandle(queryParamExtractionRoute, "localhost", 8080)
  // http://localhost:8080/api/item?id=12 , TERMINAL ==> Extracted query parameter 12

  val extractRequestRoute1: Route =
    path("controlEndpoint") {
      extractRequest { httpRequest =>
        //println(s"Got the requested path:- $httpRequest")
        //println(s"Got the requested path uri:- ${httpRequest.uri}")
        //println(s"Got the requested path method:- ${httpRequest.method}")
        //println(s"Got the requested path entity:- ${httpRequest.entity}")
        println(s"Got the requested path protocol:- ${httpRequest.protocol}")
        // check with and without HttpsContext.httpsConnectionContext
        complete(StatusCodes.OK)
      }
    }
  val extractRequestRoute2: Route =
    path("controlEndpoint") {
      extractRequest { httpRequest =>
        extractLog { log => // we can use log instead pf print
          log.info(s"Got the requested path:- ${httpRequest.uri}")
          complete(StatusCodes.OK)
        }
      }
    }
  Http().bindAndHandle(extractRequestRoute2, "localhost", 8080)
  /*SSL Error. The server certificate is invalid. Certificate has expired*/
}
