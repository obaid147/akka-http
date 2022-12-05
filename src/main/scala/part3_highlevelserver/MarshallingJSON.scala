package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

case class Player(nickName: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickName: String)
  case class GetPlayerByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}
class GameAreaMap extends Actor with ActorLogging {

  import GameAreaMap._

  var players = Map[String, Player]() // [nickName, Player]

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info(s"Getting all players")
      sender() ! players.values.toList
    case GetPlayer(nickName) =>
      log.info(s"Getting player with nick name: $nickName")
      if(!players.contains(nickName))
        sender() ! Some(Player(s"$nickName, not found", "N/A", 0))
      else
        sender() ! players.get(nickName)
    case GetPlayerByClass(characterClass) =>
      log.info(s"Getting all players with the character class:- $characterClass")
      sender() ! players.values.toList.filter(_.characterClass == characterClass)
    case AddPlayer(player) =>
      log.info(s"Trying to add player:- $player")
      players = players + (player.nickName -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player) =>
      log.info(s"Trying to remove player:- $player")
      // if(players.contains(player.nickName))
      players = players - player.nickName
      sender() ! OperationSuccess
  }
}

// step 2
trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player) // player companion object
}
object MarshallingJSON extends App
  // step 3 mixin
  with PlayerJsonProtocol
  // step 4
  with SprayJsonSupport {
  // with SprayJsonSupport, anything that can be converted to json can also be converted to toResponse Marshall-able
  implicit val system: ActorSystem = ActorSystem("MarshallingJSON")
  implicit val materializer: ActorMaterializer = ActorMaterializer.apply()
  import GameAreaMap._
  import system.dispatcher

  val gameAreaMap = system.actorOf(Props[GameAreaMap], "gameAreaMap")

  val playersList = List(
    Player("jak101", "Warrior", 70),
    Player("rolo007", "Elf", 67),
    Player("rock99", "wizard", 30)
  )
  playersList.foreach{ player =>
    gameAreaMap ! AddPlayer(player)
  }

  /*
  * -Get /api/player  => returns all players in map as JSON
  * -Get /api/player/(nickname)  => returns player with nickName in map as JSON
  * -Get /api/player?nickname=X  => returns player with nickName in map as JSON
  * -Get /api/player/class/(charClass)  => returns all player with given characterClass as JSON
  * -POST /api/player with JSON payload, adds player to the map.
  * -DELETE /api/player with JSON payload, removes player to the map.
  * */

  implicit val timeout: Timeout = Timeout(2.seconds)

  private val gameRoute =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          // TODO 1:- getAllPlayers with characterClass
          val playerByClassFuture = (gameAreaMap ? GetPlayerByClass(characterClass)).mapTo[List[Player]]
            complete(playerByClassFuture)
          //here Future[List[Player]] is automatically converted to HttpResponse (SprayJsonSupport, PlayerJsonProtocol)
        } ~
          (path(Segment) | parameter('nickname.as[String])) { nickname =>
            // TODO 2:- getPlayer with the nickname
            val playerOptionFuture: Future[Option[Player]] = (gameAreaMap ? GetPlayer(nickname)).mapTo[Option[Player]]
            complete(playerOptionFuture)
        } ~
          pathEndOrSingleSlash {
            // TODO 3:- getAllThePlayers
            complete((gameAreaMap ? GetAllPlayers).mapTo[List[Player]])
          }
      } ~
        post {
          // TODO 4:- AddAPlayer
          //entity(implicitly[FromRequestUnmarshaller[Player]]) { player =>
            /** extract HttpEntity from HttpRequest.
            * converts its payload to Player DataStructure*/
          entity(as[Player]) { player =>
            complete((gameAreaMap ? AddPlayer(player)).map(_ => StatusCodes.OK))
          }
        } ~ delete {
          // TODO 5:- DeleteAPlayer
          entity(as[Player]) { player =>
            complete((gameAreaMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
          }
      }
    }

  Http().bindAndHandle(gameRoute, "localhost", 8080)

  /** complete directive and entity directive are opposites.
   * for complete() we need to have ToResponseMarshallable.
   * for entity()   we need to have FromRequestUnmarshaller.
   *
   * implicit "playerFormat" inside trait PlayerJsonProtocol is both marshaller and un-marshaller,
   * This converts TO-and-FROM JSON.
   *
   * In entity(), there are 2 directives working.
   * 1. entity itself and
   * 2. as[] directive which fetches whatever implicit Un-marshaller there is available to compiler.
   * We can say entity(implicitly[FromRequestUnmarshaller[Player]].
   * Where implicitly fetches the implicit value of type FromRequestUnmarshaller[Player].
   * */
}
