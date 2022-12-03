package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import part2_lowlevelserver.HttpsContext

object HighLevelIntro extends App {
  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import system.dispatcher

  /*Directives
  * They are building blocks of akka-http high level apis.
  * They specify what happens under which conditions.
  *
  * path("home") directive will filter only the http requests that are trying to hit the /home path.
  * If the req passes this filter, then
  * complete(StatusCodes.ok/NotFound...) directive sends back http response with the status codes.
  *
  * The whole expression containing path() and complete() is called a Route.
  * */
  val simpleRoute: Route = // Route = (RequestContext => Future[RouteResult)
    path("home") { // Directive
    complete(StatusCodes.OK) // Directive
  }

  //Http().bindAndHandle(simpleRoute, "localhost", 8080) //HTTPie POST/GET http://localhot:8080/home
  // bindAndHandle takes a flow from HttpReq to HttpResponse, and Route can be implicitly converted to a Flow

  /** Directives inside one another...*/
  val pathGetRoute: Route = path("home") { // if http-req hits /home
    get { // if req method is GET
      complete(StatusCodes.OK) // the OK
    }
  }
  val pathPostRoute: Route = path("home") {
    post {
      complete(StatusCodes.OK)
    }
  }

  // Http().bindAndHandle(pathGetRoute, "localhost", 8080) //HTTPie GET http://localhot:8080/home
  // Http().bindAndHandle(pathPostRoute, "localhost", 8080) //HTTPie GET http://localhot:8080/home


  /** chaining routes using ~ tilde operator*/
  /* #1 Chaining inside directives*/
  val chainingRoute: Route = {
    path("myEndpoint") { // if http-req hits /myEndpoint
      get { // if req is get
        complete(StatusCodes.OK) // then 200 OK
      } ~ // using tilde operator chain pot req
      post { // if http-req is post
        complete(StatusCodes.Forbidden) // the 403 Forbidden
      }
    } ~  /* #2 chaining outside directives*/
    path("home") { // works with most HttpMethods
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<h2><center><u> Hello from the high level akka-http </u></center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    }
  } // Chaining of routes results in "RoutingTree"
  /** if we missed tilde operator, the last method will be considered as the HttpMethod, (last expression is returned)*/

  // Http().bindAndHandle(chainingRoute, "localhost", 8080)


  /** Both Low and High level API use same bindAndHandle, Therefore We can use the certificate for HTTPS here also*/
  Http().bindAndHandle(chainingRoute, "localhost", 8080, HttpsContext.httpsConnectionContext)
  // There we go, Now we have an Https server based on the high level high level API.
}
