package playground

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.io.StdIn

object Playground extends App {

  implicit val system: ActorSystem = ActorSystem("AkkaHttpPlayground")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    pathEndOrSingleSlash {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <title> Akka HTTP </title>
          | <body>
          |   <h3><u>
          |     <center>Akka HTTP is working on port 8081</center>
          |   </h3></u>
          | </body>
          |</html>
        """.stripMargin
      ))
    }

  val bindingFuture: Future[Http.ServerBinding] =
    Http().bindAndHandle(simpleRoute, "localhost", 8081)
  // wait for a new line, then terminate the server
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

//  val l = List(2).flatMap(Seq(_))
}
