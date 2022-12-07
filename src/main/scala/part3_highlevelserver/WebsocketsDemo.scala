package part3_highlevelserver

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.CompactByteString

import scala.concurrent.duration.DurationInt


/**Bidirectional communication between client/frontend and server(akka-http).
 * Websocket is established by an upgraded http/https connections using special headers:-
 * 1. connection: upgrade
 * 2. upgrade: websocket
 * Once the server understand these headers, It will upgrade a connection to websocket, which will
 * then establish a medium for exchanging messages.
 * Messages can be sent both ways and in particular, The SERVER can push data to client! (fb feed/ instagram feed)
 */
object WebsocketsDemo extends App {
    implicit val system: ActorSystem = ActorSystem("WebsocketsDemo")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    /*Messages between client and server
    * 1- Text Messages (Text Based Message sent by frontend or by the server)
    * 2- Binary Messages (Binary message can contain everything:- sounds, videos...etc)
    */

    /** Setting up messages that can be exchanged by client and server
     * Akka-HTTP models it by message type which has 2 subtypes:-
     * Text Message and Binary Message
     */
    val textMessage = TextMessage(Source.single("hello via text message!"))
    // Text Messages are wrapper over source and that source will emits Strings.
    // The reason why TextMessage is wrapped a Source is because the messages that can be
    // exchanged in both ways can have an arbitrary sides, which is why AKKA_HTTP models those as STREAMS.

    val binaryMessage = BinaryMessage(Source.single(CompactByteString("Hello via a Binary message.")))
    // Binary Messages are wrapper over sources that emits ByteStrings.
    // So, the binaryMessage will contains bytes that composes the string.
    // CompactByteString encodes our "" string as bytes.
    // Binary message can contain everything:- sounds, videos, text....etc

    /** preparing to send messages through websocket.
     * To open websocket, we need to send JS/HTML to browser frontend/client, that will
     * initiate the websocket to me(server)*/
    //HTML Folder under main., websockets.html

    val html ="""
      |<html>
      |    <body>
      |        <head>
      |            <script>
      |                var exampleSocket = new WebSocket("ws://localhost:8080/greeter");
      |                // instead of http/https, we are using ws (websocket)
      |                console.log("starting websocket....");
      |
      |                exampleSocket.onmessage = function(event){
      |                    // client receives msg from server. onmessage is a member of ws
      |                    var newChild = document.createElement("div");
      |                    newChild.innerText = event.data;
      |                    // event.data is whatever data server sends
      |                    document.getElementById("1").appendChild(newChild);
      |                    //append child to div from body.
      |                };
      |
      |                exampleSocket.onopen = function(event){
      |                    // onopen is a mamber of ws
      |                    exampleSocket.send("socket seems to be open...");
      |                    // client will send this message to server and can be seen by our akka-http server.
      |                };
      |
      |                // When socket is open, we will send message to server
      |                exampleSocket.send("socket says: Hello, Server!!");
      |            </script>
      |
      |        </head>
      |    </body>
      |        Starting websocket...
      |        <div id="1">
      |            <!--client receives msg from server are manipulated is JS -->
      |        </div>
      |</html>
      |""".stripMargin

    def webSocketFlow: Flow[Message, Message, Any] = Flow[Message].map { /*Message superType of Text&Binary Message*/
        case txtMsg: TextMessage =>
            TextMessage(Source.single("Server say back:") ++ txtMsg.textStream ++ Source.single("!"))
            /*concat Source with textStream so we can decompose TextMessage into its constituent component
            * - textMessage.textStream is the content of the original text that the client send to us server*/
        case biMsg: BinaryMessage =>
            biMsg.dataStream.runWith(Sink.ignore)
            // run stream with Sink.ignore in order to exhaust data in binaryMsg. otherwise, we may leak resources.
            TextMessage(Source.single("Server received binary message"))
    }

    val websocketRoute =
        (pathEndOrSingleSlash & get) {
            complete(
                HttpEntity(
                ContentTypes.`text/html(UTF-8)`, html
                )
            )
        } ~
            path("greeter") {
                // directive for handling websocket messages that takes a flow from message => message
                handleWebSocketMessages(socialFlow)
            }

    Http().bindAndHandle(/*webSocketFlow*/websocketRoute, "localhost", 8080)

    /*push data to client*/
    case class SocialPost(owner: String, content: String)

    val socialFeed: Source[SocialPost, NotUsed] = Source {
        List(
            SocialPost("Martin", "Scala 3 has been announced."),
            SocialPost("Obaid", "working akka-http sockets"),
            SocialPost("Martin", "learn scala")
        )
    }

    val socialMessages =
        socialFeed.map(
        socialPost => TextMessage(s"${socialPost.owner} said: ${socialPost.content}")
        )
        .throttle(1, 2.seconds)

    val socialFlow: Flow[Message, Message, Any] = Flow.fromSinkAndSource(
        // fromSinkAndSource where input is a SINK and output is a SOURCE and are not connected.
        Sink.foreach[Message](println),
        socialMessages
    )
}
