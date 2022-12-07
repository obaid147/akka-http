package part3_highlevelserver

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString

import java.io.File
import scala.concurrent.Future
import scala.util.{Failure, Success}

object UploadingFiles extends App {
    implicit val system: ActorSystem = ActorSystem("UploadingFiles")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    val filesRoute: Route =
        (pathEndOrSingleSlash & get) {
            complete(
                HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                      |<html>
                      |  <body>
                      |    <form action="http://localhost:8080/upload" method="post" enctype="multipart/form-data">
                      |      <input type="file" name="myFile">
                      |      <button type="submit">Upload</button>
                      |    </form>
                      |  </body>
                      |</html>
                  """.stripMargin
                )
            )
        } ~
        (path("upload") & extractLog) { log =>
            /*uploading file is done by sending httpEntities called multipart/form-data */
            entity(as[Multipart.FormData]) { formData =>
                // parse http-entity as Multipart.FormData, If success, Turn formData into Source
                // formData will contain file payload. and will handled as source in akk-http representation.
                val partsSource: Source[Multipart.FormData.BodyPart, Any] = formData.parts
                // Source of chunks that file may contain.

                //dump source to sink, In order to this file on akka-http-disk, we need to feed source to sink
                val filePartsSink: Sink[Multipart.FormData.BodyPart, Future[Done]] =
                    Sink.foreach[Multipart.FormData.BodyPart]{ bodyPart =>
                        if (bodyPart.name == "myFile") {
                            // create a file so that we can dump the bytes/payload of bodyPart into that file.
                            val filename = "src/main/resources/upload_download/" +
                                bodyPart.filename.getOrElse("tempFile_" + System.currentTimeMillis())

                            val file = new File(filename)

                            log.info(s"writing to file $filename")

                            val fileContextSource: Source[ByteString, Any] = bodyPart.entity.dataBytes
                            val fileContextSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(file.toPath)

                            // writing the data to the file.
                            fileContextSource.runWith(fileContextSink)
                        }
                    }
                /** we need materialized value of Sink in order to ACK when the uploading has completed*/

                // run an akka stream from partsSource ~> filePartsSink
                val writeOperationFuture: Future[Done] = partsSource.runWith(filePartsSink)

                onComplete(writeOperationFuture) {//takes future as arg
                    case Success(_) => complete("File uploaded")
                    case Failure(ex) => complete(s"File uploading failed: $ex")
                }

            }
        }

    Http().bindAndHandle(filesRoute, "localhost", 8080)
    // after uploading the file is can be located under resources/download folder
}
