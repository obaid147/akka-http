package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object HighLevelExample extends App with GuitarStoreJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("HighLevelExample")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import GuitarDB._
  import system.dispatcher
  /*
    GET /api/guitar fetches ALL the guitars in the store
    GET /api/guitar?id=x fetches the guitar with id X
    GET /api/guitar/X fetches guitar with id X
    GET /api/guitar/inventory?inStock=true
   */
  /* SETUP */
  val guitarDb = system.actorOf(Props[GuitarDB], "lowLevelGuitarDB")
  val guitarList = List(Guitar("Fender", "Stratocaster"), Guitar("Gibson", "Les paul"), Guitar("Martin", "LX1"))
  guitarList.foreach(guitar => guitarDb ! CreateGuitar(guitar))

  implicit val defaultTimeout: Timeout = Timeout(3.seconds)

  val guitarServerRoute =
    path("api" / "guitar") {
      parameter('id.as[Int]) { guitarId => // Always Put More Specific Route First
        get {
          val guitarFuture = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      get {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        val entityFuture = guitarsFuture.map { guitars =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
      path("api" / "guitar" / IntNumber) { guitarId =>
        get {
          val guitarFuture = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
          parameter('inStock.as[Boolean]) { inStock =>
            val guitarFuture = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
            val entityFuture = guitarFuture.map { guitars =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            }
            complete(entityFuture)
          }
        }
      }

  //Http().bindAndHandle(guitarServerRoute, "localhost", 8080)

  def toHttpEntity(payload: String) =
    HttpEntity(
      ContentTypes.`application/json`,
      payload
    )

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {

      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          complete(
            (guitarDb ? FindGuitarsInStock(inStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
         (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
           complete(
             (guitarDb ? FindGuitar(guitarId))
               .mapTo[Option[Guitar]]
               .map(_.toJson.prettyPrint)
               .map(toHttpEntity)
           )
         } ~
          pathEndOrSingleSlash {
            complete(
              (guitarDb ? FindAllGuitars)
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
            )
          }
      }

  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8080)
}
