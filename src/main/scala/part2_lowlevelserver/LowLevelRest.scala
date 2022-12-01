package part2_lowlevelserver


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
// step #1
import spray.json._ // contains a suite of convertors, implicits, utilities that we use with conversions...

/**
 * Marshalling-Unmarshalling JSON
 * */
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
      sender() ! GuitarCreated(currentGuitarId)
      guitars += (currentGuitarId -> guitar)
      currentGuitarId += 1
  }
}

class Helper extends Actor with ActorLogging {
  import GuitarDB._
  val actor: ActorRef = context.actorOf(Props[GuitarDB], "C")

  override def receive: Receive = {
    case FindGuitar(id) =>
      actor ! FindGuitar(id)
    case CreateGuitar(guitar) =>
      actor ! CreateGuitar(guitar)
    case FindAllGuitars =>
      actor ! FindAllGuitars
    case msg: Any =>
      log.info(s"----------------$msg")
  }
}


// step #2
/**
 * DefaultJsonProtocol Provides all the predefined JsonFormats.
 */
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step #3
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat2(Guitar)
  // being able to convert Guitar into json, json2 as Guitar take 2 args
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  /* GET request on localhost:8080/api/guitar => response(all guitars in store) in JSON
  *  POST req on localhost:8080/api/guitar => Insert guitar into the store ie: DB.
  */

  //serialize our response(eg: guitar list) to JSON and pass that as an http entity(payload http response)
  //deserialize the data in the form that app can understand when client hits POST request.
  /**Marshalling
   * process of serializing our data to a wire format that an HTTP client can understand.
   * Unmarshalling == vice-versa
   * We do this by using a library called spray-json included in build.sbt as akka-http-spray-json
  */

  // My object(Guitar) to JSON -> marshalling
  val simpleGuitar: Guitar = Guitar("Fender", "Stratocaster")
  val guitarAsJSON: String = simpleGuitar.toJson.prettyPrint
  /** .toJson converts simpleGuitar to intermediate json then .prettyPrint makes it digestible */
  println(guitarAsJSON) // print it in JSON format



  // unmarshalling means turning json string to actual Guitar
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
        |  "model": "Stratocaster"
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])
  /** .parseJson method turns this string and enriches it via an implicit conversion to an intermediate
   * JSON ast (abstract syntax tree) then .convertTo[Guitar] converts it to Guitar because there is an implicit
   * "guitarFormat" implemented earlier*/




  /*import GuitarDB._
  val actor = system.actorOf(Props[Helper], "Helper")
  actor ! CreateGuitar(Guitar("MAKE1", "MODEL1"))
  actor ! FindAllGuitars
  actor ! FindGuitar(0)
  actor ! CreateGuitar(Guitar("MAKE2", "MODEL2"))
  actor ! FindAllGuitars
  actor ! FindGuitar(1)
  Thread.sleep(200)
  actor ! FindGuitar(2)*/
}
