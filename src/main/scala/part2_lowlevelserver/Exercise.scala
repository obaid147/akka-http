package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.CreateGuitar
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt // contains a suite of convertors, implicits, utilities that we use with conversions...

/** EXERCISE Enhance Guitar case class with quantity field, by default 0
 * - GET /api/guitar/inventory?inStock=true/false which returns the guitar in stock as a json.
 * - POST /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar id X.
 */
case class Guitar(make: String, model: String, quantity: Int=0)
object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
}
class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("searching for all guitars")
      sender() ! guitars.values.toList
    case FindGuitar(id) =>
      log.info(s"searching guitar by id: $id")
      sender() ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding Guitar $guitar with id: $currentGuitarId")
      sender() ! GuitarCreated(currentGuitarId)
      guitars += (currentGuitarId -> guitar)
      currentGuitarId += 1
  }
}
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step #3
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat3(Guitar)
  // being able to convert Guitar into json, json2 as Guitar take 2 args
}
object Exercise extends App with GuitarStoreJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import system.dispatcher

  val guitarDb = system.actorOf(Props[GuitarDB], "lowLevelGuitarDB")
  val guitarList = List(Guitar("Fender", "Stratocaster"), Guitar("Gibson", "Les paul"), Guitar("Martin", "LX1"))
  guitarList.foreach(guitar => guitarDb ! CreateGuitar(guitar))

  implicit val timeout: Timeout = 2.seconds

  import GuitarDB._

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      /* Query parameter*/
      val query = uri.query() // query object <=> Map[String, String]

      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint // implicit required...
            )
          )
        } // Future[List[Guitar]] to Future[HttpResponse] (GET ALL GUITARS)
      } else {
        // fetch guitar associated to guitar Id, localhost:8080/api/guitar?id=1&id=2
        //getGuitar(query)
      }
  }

  /** EXERCISE Enhance Guitar case class with quantity field, by default 0
   * - GET /api/guitar/inventory?inStock=true/false which returns the guitar in stock as a json.
   * - POST /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar id X.
   */
}
