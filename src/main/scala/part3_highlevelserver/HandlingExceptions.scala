package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.ActorMaterializer


/** Exceptions as opposed to Rejections are not being aggregated by the RoutingTree.
 * No problem as handling exceptions by priority as we saw with Rejections List().
*/
object HandlingExceptions extends App {

    implicit val system: ActorSystem = ActorSystem("HandlingExceptions")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    val simpleRoute =
        path("api" / "people") {
            get {
                // directive that throws some exception
                throw new RuntimeException("Getting all the people took too long")
            } ~
                post {
                    parameter('id) { id =>
                        if (id.length > 2)
                            throw new NoSuchElementException(s"Parameter $id cannot be found in the database, TABLE FLIP!")

                        complete(StatusCodes.OK)
                    }
                }
        }

    // way #1
    implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
        case ex: RuntimeException =>
            complete(StatusCodes.NotFound, ex.getMessage)
        case ex: IllegalArgumentException =>
            complete(StatusCodes.BadRequest, ex.getMessage)
    }

    //Http().bindAndHandle(simpleRoute, "localhost", 8080)
    /**If a directive throws exception which is not handled by our implicit ExceptionHandler,
    * the default exception handler handles it.*/

    // way #2 same as RejectHandler
    val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
        case ex: RuntimeException =>
            complete(StatusCodes.NotFound, ex.getMessage)
    }

    val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
        case ex: NoSuchElementException =>
            complete(StatusCodes.BadRequest, ex.getMessage)
    }

    //handleExceptions is basically same a try catch
    val delicateHandleRoute = {
        handleExceptions(runtimeExceptionHandler) { // top level handler |
            // http://localhost:8080/api/people GET
            path("api" / "people") {
                get {
                    // directive that throws some exception
                    throw new RuntimeException("Getting all the people took too long")
                } ~
                    handleExceptions(noSuchElementExceptionHandler) { // inner handler
                        // http://localhost:8080/api/people?id=aaa POST
                        post {
                            parameter('id) { id =>
                                if (id.length > 2)
                                    throw new NoSuchElementException(s"Parameter $id cannot be found in the database, TABLE FLIP!")

                                complete(StatusCodes.OK)
                            }
                        }
                    }
            }
        } // if exception,runtimeExceptionHandler. else default handler 500.
    }

    Http().bindAndHandle(delicateHandleRoute, "localhost", 8080)

}