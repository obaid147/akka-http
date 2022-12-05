package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler}
import akka.stream.ActorMaterializer

/**
 * If a request doesn't match a directive, which is a filter, It's called rejected request.
 * reject = pass the request to whatever is next in the routing tree to possibly match.
 * Rejection is not a failure.
 *
 * Rejections are aggregated:-
 * When HttpRequest comes in which is handled by RoutingTree, an empty list[PossibleRejections] is created, So that
 * it can store the possible rejections by the directives that compose this routing tree
 * example:-
 * get {//route1}~post{//route2}~parameter('myParam){myparam =>//route3}
 * if req is not GET, add a MethodRejectionObject to the Rejection List.
 * if req is not POST, add another MethodRejectionObject to the Rejection List.
 * so on and so forth until one of the directives can match the HTTP request.
 *
 * If req matches parameter, clear the rejection list for new possible rejections.
 * */
object HandlingRejections extends App {
    implicit val system: ActorSystem = ActorSystem("HandlingRejections")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val simpleRoute =
        path("api" / "myEndpoint") {
            get {
                complete(StatusCodes.OK)
            } ~
              parameter('id) { _ =>
                  complete(StatusCodes.OK)
              }
        }


    // Rejection Handlers
    /*RejectionHandler is a fn immutable.Seq[Rejection] ⇒ Option[Route] */

    val badRequestHandler: RejectionHandler = { rejection: Seq[Rejection] =>
        println(s"Encountered Rejection $rejection")
        Some(complete(StatusCodes.BadRequest))
    }
    val forbiddenHandler: RejectionHandler = { rejection: Seq[Rejection] =>
        println(s"Encountered Rejection $rejection")
        Some(complete(StatusCodes.Forbidden))
    }

    val simpleRouteWithHandlers =
        handleRejections(badRequestHandler) { // handle rejection from top level
            //define server logic
            path("api" / "myEndpoint") {
                get {
                    complete(StatusCodes.OK)
                } ~ // if req is not get, handleRejections(param) param/handler will handle that rejection.
                post{
                    handleRejections(forbiddenHandler) { // handle rejection within
                        parameter('myParam) { _ =>
                            complete(StatusCodes.OK)
                        }
                    }
                }
            }
        }

    //Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8080)
    /**
     * List(MissingQueryParamRejection(myParam)), POST http://localhost:8080/api/myEndpoint?someOtherParam=10
     * List(MissingQueryParamRejection(myParam)), POST http://localhost:8080/api/myEndpoint
     * List(MethodRejection(HttpMethod(GET)), MethodRejection(HttpMethod(POST))), PUT http://localhost:8080/api/myEndpoint
     *
     * List(), POST/GET http://localhost:8080/api/someOtherEndpoint    =>>>> NOT-FOUND
     *
     *
     * If we don't write the rejection handlers, RejectionHandler.default will be passed automatically
            RejectionHandler.default
     * */


    // implicit Rejection Handler
    /** RejectionHandler is called "SEALING A ROUTE".
     * No matter what HTTP request you get in your route, you always have a defined action for it.*/
    implicit val customRejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
        .handle {
            case m: MissingQueryParamRejection =>
                println(s"Encountered Rejection(custom):- $m")
                complete("Rejected query param!")
            /*case m: MethodRejection =>
                println(s"Encountered Rejection(custom):- $m")
                complete("Rejected method!")*/
        }
        .handle {
            case m: MethodRejection =>
            println(s"Encountered Rejection(custom):- $m")
            complete("Rejected method!")
        }
        .result() //to build an actual rejection handler

    Http().bindAndHandle(simpleRoute, "localhost", 8080)
    /**customRejectionHandler will be automatically added to simpleRoute as it's rejection handler because implicit.
     *
     * 1. POST http://localhost:8080/api/myEndpoint  Encountered Rejection(custom):- MethodRejection(HttpMethod(GET))
     * 2. POST http://localhost:8080/api/myEndpoint?xyz=10   same and not MissingQueryParamRejection(id)
     * because our implicit handler has created a list like List(MethodRejection, MissingQueryParamRejection)
     * If we interchange .handle{MissingQueryParamRejection} first teh MethodRejection and HIT
     * POST http://localhost:8080/api/myEndpoint?xyz=10   =>
     * we will get MissingQueryParamRejection(id) and Rejected query param!....
     *
     * Entire List of Rejection is matched with all cases until one of the Rejection matches on of the case.
     * If no rejections in the list match any of the cases in one of the handles, then the next handle will
     *  be tested.
     * So adding multiple handles with a single case basically adds a priority for the kind of rejections that
     * we want to test first.
     *
     *
     * Move both cases in first handle, restart application and test.
     *
     *
     * Multiple handles with single case each is different from a single handle with multiple cases.
     * */
}
