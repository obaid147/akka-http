package part2_lowlevelserver


import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import part2_lowlevelserver.GuitarDB.CreateGuitar

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

// step #1
import spray.json._ // contains a suite of convertors, implicits, utilities that we use with conversions...

/**
 * Marshalling-Unmarshalling JSON
 * */
case class Guitar(make: String, model: String, quantity: Int = 0)

/** EXERCISE Enhance Guitar case class with quantity field, by default 0
 * - GET /api/guitar/inventory?inStock=true/false which returns the guitar in stock as a json.
 * - POST /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar id X.
 */
object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
  case class AddQuantity(id: Int, quantity: Int) // POST api/guitar/inventory
  case class FindGuitarsInStock(inStock: Boolean) // GET api/guitar/inventory
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
    case AddQuantity(id, quantity) =>
      log.info(s"Trying to add $quantity items for guitar $id")
      val guitar = guitars.get(id)
      val newGuitar: Option[Guitar] = guitar.map{
        case Guitar(make, model, q) => Guitar(make, model, q + quantity)
      }
      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar
    case FindGuitarsInStock(inStock) =>
      log.info(s"Searching for all guitars ${if(inStock)"in" else "out of"} stock")
      if(inStock) sender() ! guitars.values.filter(_.quantity > 0)
      else sender() ! guitars.values.filter(_.quantity==0)
  }
}

// step #2
/**
 * DefaultJsonProtocol Provides all the predefined JsonFormats.
 */
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  // step #3
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat3(Guitar)
  // being able to convert Guitar into json, json2 as Guitar take 3 args (exercise) otherwise jsonFormat2
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  /* GET request on localhost:8080/api/guitar => response(all guitars in store) in JSON
  *  GET on localhost:8080/api/guitar?id=X    => fetch guitar associated with id=X
  *  POST req on localhost:8080/api/guitar    => Insert guitar into the store ie: DB.
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
        |  "model": "Stratocaster",
        |  "quantity": 3
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])
  /** .parseJson method turns this string and enriches it via an implicit conversion to an intermediate
   * JSON ast (abstract syntax tree) then .convertTo[Guitar] converts it to Guitar because there is an implicit
   * "guitarFormat" implemented earlier*/


  /* SETUP */
  val guitarDb = system.actorOf(Props[GuitarDB], "lowLevelGuitarDB")
  val guitarList = List(Guitar("Fender", "Stratocaster"), Guitar("Gibson", "Les paul"), Guitar("Martin", "LX1"))
  guitarList.foreach(guitar => guitarDb ! CreateGuitar(guitar))


  /*Server code with AsyncHandler fn: HttpRequest => Future[HttpResponse]*/
  implicit val timeout: Timeout = 2.seconds
  import GuitarDB._

  def getGuitar(query: Query): Future[HttpResponse] = {
    val id = query.get("id")
    try {
      id.map(_.toInt)
       // query.get("id") returns Option[String], needed Option[Int]
    }catch {
      case _: Throwable =>
        HttpResponse(StatusCodes.InternalServerError)
        println("---Exception OCCURRED!!! Please use the ID---")
    }

    val guitarId = query.get("id").map(_.toInt)
    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound)) // /api/guitar?id=      => 404
      case Some(id: Int) =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map{
          case None => HttpResponse(StatusCodes.NotFound) // /api/guitar?id=IdThatDoesn'tExistInDB
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
            ))
        }
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val guitarId: Option[Int] = query.get("id").map(_.toInt)
      val guitarQuantity: Option[Int] = query.get("quantity").map(_.toInt)

      val validGuitarResponseFuture: Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture: Future[Option[Guitar]] = (guitarDb ? AddQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }
      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val inStockOption = query.get("inStock").map(_.toBoolean)
      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map{ guitars =>
            HttpResponse(
              entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
              )
            )
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }



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
        }// Future[List[Guitar]] to Future[HttpResponse] (GET ALL GUITARS)
      } else {
        // fetch guitar associated to guitar Id, localhost:8080/api/guitar?id=1&id=2
        getGuitar(query)
      }
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entities are a source[ByteString]
      val strictEntityFuture: Future[HttpEntity.Strict] = entity.toStrict(3.seconds)
      /**akka will bring all the contents of this entity into memory from http-conn during the course of 3 seconds
       * Because we don't know when this operation will succeed, AKKA-HTTP returns a future containing the
       * data from this entity.*/
      strictEntityFuture.flatMap{ strictEntity =>
        val guitarJson: ByteString = strictEntity.data
        val guitarJsonString: String = guitarJson.utf8String
        val guitar: Guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        /*We have two Futures, instead of strictEntityFuture.map, we can strictEntityFuture.flaMap. So the function
         * inside needs to return Future[HttpResponse].
        * Now, we will convert Future[GuitarCreated] into Future[HttpResponse]*/
        guitarCreatedFuture.map{ _ =>
          HttpResponse(StatusCodes.OK)
        }
      }// Insert a guitar into the store

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
    /** If We don't reply/respond to an existing request, that will be interpreted as backPressure.
     *  That backPressure is interpreted by the Streams based akka-http-server and that will be propagated
     *  down to TCP layer. which results in slower HttpRequests...*/
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)
    /* Once we run the application and hit (using HTTPie)

    (GET)localhost:8080/api/guitar  -> Console => searching for all guitars, HTTPie => we get all the guitars

    (POST)localhost:8080/api/guitar  -> Console => Adding Guitar Guitar(Taylor,914) with id: 3,
    HTTPie => HTTP/1.1 200 OK

    BROWSER-> localhost:8080/api/guitar/   => We will see the added Guitar to the list as json*/

  /** EXERCISE Enhance Guitar case class with quantity field, by default 0
   * - GET /api/guitar/inventory?inStock=true/false which returns the guitar in stock as a json.
   * - POST /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar id X.
   */
}
