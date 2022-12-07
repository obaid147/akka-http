package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

/**Authentication verifies the identity of a user or service, and authorization determines their access rights.
 *
 * JWT(JSON Web Token)
 * eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.
 * SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
 * JWT Structure consists of three parts separated by dot.:-
 * 1. header :-
 *  It' the encoding of below JSON
 *  {
 *      "typ": "JWT",       # Type as JWT.
 *      "alg": "HS256"      # HashingAlgorithm that will be used for signatures.
 *                          # The signature is used to verify that the sender of the JWT is who
 *                          # it says it is and to ensure that the message wasn't changed along the way.
 *                          # This JSON is encoded Base64 to obtain eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
 *  }
 *
 *  2. payload (claims):-
 *  {
 *      "iss": "example.com",
 *      "exp": 1300819380,
 *      "name": "oby oby",
 *      "admin": true
 *  }
 *  This contains some registered claims (iss => issuer and exp => expiration date). and
 *  Custom data (public claims), This can contain anything like name, permission...etc
 *
 *  This is also encoded BASE64 to obtain the eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ
 *
 *  3. Signature:- is where hashing / cryptographic algorithm comes into play
 *  take  encoded header+"."+encoded claims   (header+"."payload)
 *  (encrypt)/sign it with the algorithm in the header(HS256) and a secret key that is specified beforehand(in advance)
 *  in some code/configuration file, we obtain a value.
 *  and that value of is then encoded Base64 to obtain the final value(signature).
 *
 *  The header + payload/claim + signature is compacted into JWT.
 * */

object SecurityDomain extends DefaultJsonProtocol {
    case class LoginRequest(username: String, password: String)
    implicit val loginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
}
object JwtAuthorization extends App with SprayJsonSupport {
    implicit val system: ActorSystem = ActorSystem("JwtAuthorization")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import SecurityDomain._
    import system.dispatcher

    val superSecretPasswordD = Map("admin" -> "admin", "obaid" -> "obaid1!")
    val algorithm = JwtAlgorithm.HS256
    val secretKey = "akkaHttpSecretKey" //fetch from secure place.

    def checkPassword(username: String, pasword: String): Boolean = {
        superSecretPasswordD.contains(username) && superSecretPasswordD(username) == pasword
    }

    def createToken(username: String, expirationPeriodInDays: Int): String = {
        val claims = {
            JwtClaim(
                expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
                issuedAt = Some(System.currentTimeMillis() / 1000),
                issuer = Some("example.com")
                //content = "ADMIN"
            )
        }
        JwtSprayJson.encode(claims, secretKey, algorithm) // JWT string
    }

    def isTokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
        case Success(claims) => claims.expiration.getOrElse(0L) < (System.currentTimeMillis() / 1000)
        case Failure(_) => true
    }
    def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

    val loginRoute =
        post {
            entity(as[LoginRequest]) { // decompose payload as LoginRequest
                case LoginRequest(username, password) if checkPassword(username, password) =>
                    // if username and password is correct, create JWT token
                    val token = createToken(username, 1) // token expiry 1 day and will be invalid after that.
                    respondWithHeader(RawHeader("Access-Token", token)) {
                        complete(StatusCodes.OK)
                    }
                case _ =>
                    complete(StatusCodes.Unauthorized)
            }
        }

    val authenticatedRoute =
        (path("secureEndpoint") & get) {
            optionalHeaderValueByName("Authorization") {
                case Some(token) =>
                    if (isTokenValid(token)) {
                        if (isTokenExpired(token)) {
                            complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired."))
                        } else {
                            complete("User accessed authorized endpoint!")
                        }
                    } else {
                        complete(HttpResponse(status = StatusCodes.Unauthorized,
                            entity = "Token is invalid, or has been tampered with."))
                    }
                case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "No token provided!"))
            }
        }

    val route = loginRoute ~ authenticatedRoute

    Http().bindAndHandle(route, "localhost", 8080)

    /** HTTPie
     * POST http://localhost:8080/
     * Body
     * {
     * "username": "obaid",
     * "password": "obaid1!"
     * }
     *
     * copy the Access-Token
     * GET http://localhost:8080/secureEndpoint
     * Header Tab
     * Authorization as name and pasteTheToken as value.
     *
     * User accessed authorized endpoint!.............
     */
}
