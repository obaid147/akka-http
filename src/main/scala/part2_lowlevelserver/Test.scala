package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.Future
import akka.http.scaladsl.Http.IncomingConnection
import akka.stream.ActorMaterializer

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}


object Test extends App {
  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] = {
    Http().bind(interface = "localhost", port = 8080)
  }

  val connectionsSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from ${connection.remoteAddress}")
  }

  val serverBindingFuture: Future[Http.ServerBinding] = serverSource.to(connectionsSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) =>
      println("server binding successful")
      binding.terminate(2.seconds)
    case Failure(ex) => println(s"server binding failed:- $ex")
  }

}
