package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsContext {
  //#1 initialize key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12") // from resources
  // new FileInputStream(new File("src/main/resources/keystore.pkcs12")) // alternative
  val password: Array[Char] = "akka-https".toCharArray

  /** Never store password in code explicitly, fetch them from secure DB or from secure source */
  ks.load(keyStoreFile, password)

  // #2 initialize key manager
  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509") // PKI, public key infrastructure.
  keyManagerFactory.init(ks, password)

  // #3 initialize a trust manager
  val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // #4 initialize an SSL context (secret socket layer)
  val sslContext: SSLContext = SSLContext.getInstance("TLS") // Transport Layered Security
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  // #5 return the HTTPS connection context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)
}


object LowLevelHttps extends App {
  implicit val system: ActorSystem = ActorSystem("lowLevelHttps")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import HttpsContext._
  val requestHandlerSynchronous: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<h2><center>Hello from akka http</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, //404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |<br><br>
            |<h2><center>OOPS! The resource cannot be found</center></h2>
            |</body>
            |</html>
            |""".stripMargin
        )
      )
  }

  Http().bindAndHandleSync(requestHandlerSynchronous, "localhost", 8443, httpsConnectionContext)
}
