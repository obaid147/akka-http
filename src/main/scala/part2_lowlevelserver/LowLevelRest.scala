package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.stream.ActorMaterializer
// step #1
import spray.json._ // contains a suite of convertors, implicits, utilities that we use with conversions...

case class Guitar(make: String, model: String)

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
      guitars += (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1

  }

}


// step #2
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {

  // step #3
  implicit val guitarFormat = jsonFormat2(Guitar) // being able to convert Guitar into json, json2 as Guitar take 2 args

}

object LowLevelRest extends App with GuitarStoreJsonProtocol {
  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  /* GET request on localhost:8080/api/guitar => response(all guitars in store) in JSON
  *  POST req on localhost:8080/api/guitar => Insert guitar into the store
  */

  //serialize our response(eg: guitar list) to JSON and pass that as an http entity(payload http response)
  //deserialize the data in the form that app can understand when client hits POST request.
  /**Marshalling
   * process of serializing our data to a wire format that an HTTP client can understand.
   * Unmarshalling == vice-versa
   * We do this by using a library called spray-json included in build.sbt as akka-http-spray-json
  */

  // JSON -> marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling means turning json string to actual guitar
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster"
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])
}
