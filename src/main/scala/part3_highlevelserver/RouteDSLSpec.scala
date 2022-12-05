package part3_highlevelserver

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
case class Book(id: Int, author: String, title: String)


trait BookJsonProtocol extends DefaultJsonProtocol {
    implicit val bookFormat: RootJsonFormat[Book] = jsonFormat3(Book)
}
class RouteDSLSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {

    import RouteDSLSpec._

    "A digital library backend" should { // TestSuite
        "return all the books in the library" in { // Test1
            // send an HTTP request through an endpoint that you want to test
            // inspect the response or anything related to response.
            Get("/api/book") ~> libraryRoute ~> check {
                /** Get("/api/book") feeds into libraryRoute feed into check directory where we do assertions*/
                status should be (StatusCodes.OK)

                //entityAs[List[Book]] // check the entity which is json and convert it to List[Book]
                entityAs[List[Book]] shouldBe books
            }
        }
        "return a book by hitting the query parameter" in {
            Get("/api/book?id=1") ~> libraryRoute ~> check {
                entityAs[Book] shouldBe books.head
                responseAs[Option[Book]] shouldBe Some(Book(1, "Harper Lee", "To Kill a Mockingbird"))
            }

        }
        "return a book by calling end point with the path" in {
            Get("/api/book/4") ~> libraryRoute ~> check { // manual
                response.status shouldBe StatusCodes.OK
                responseAs[Option[Book]] shouldBe Some(books.last)

                val strictEntityFuture: Future[HttpEntity.Strict] = response.entity.toStrict(1.second)
                val strictEntity = Await.result(strictEntityFuture, 1.seconds)
                strictEntity.contentType shouldBe ContentTypes.`application/json`

                val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
                book shouldBe Some(Book(4, "Tony Robbins", "Awaken the Giant Within"))
            }
        }
        "insert a book into the 'database'" in {
            val newBook = Book(5, "Steven Pressfield", "The War of Art")
            // sending payload(newBook) to post req
            Post("/api/book", newBook) ~> libraryRoute ~> check {
                status shouldBe StatusCodes.OK
                assert(books.contains(newBook))
                books should contain(newBook) //same
            }
        }
        "not accept other methods than POST and GET" in {
            //status shouldBe StatusCodes.InternalServerError
            Delete("/api/book") ~> libraryRoute ~> check {
                rejections should not be empty
                rejections.should(not).be(empty)//same

                val methodRejection: immutable.Seq[MethodRejection] = rejections.collect{
                    case rejection: MethodRejection => rejection
                }
                methodRejection.length shouldBe 2
            }
        }
        "return all books by the author" in {
            Get("/api/book/author/Harper%20Lee") ~> libraryRoute ~> check { // url encoding for space is %20
                status shouldBe StatusCodes.OK
                entityAs[List[Book]] shouldBe books.filter(_.author == "Harper Lee")
            }
        }
    }
}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport {

    // code under test
    private var books: List[Book] = List(
        Book(1, "Harper Lee", "To Kill a Mockingbird"),
        Book(2, "JRR Tolkien", "The Lord of the Rings"),
        Book(3, "GRR Marting", "A Song of Ice and Fire"),
        Book(4, "Tony Robbins", "Awaken the Giant Within")
    )

    /*
      GET /api/book - returns all the books in the library
      GET /api/book/X - return a single book with id X
      GET /api/book?id=X - same
      POST /api/book - adds a new book to the library
      GET /api/book/author/X - returns all the books from the actor X
      Get /api/book/author/X - returns all the books from the actor X
     */
    private val libraryRoute: Route =
        pathPrefix("api" / "book") {
            (path("author" / Segment) & get) { author =>
               complete(books.filter(_.author == author)) // list of books
            } ~
                get {
                    (path(IntNumber) | parameter('id.as[Int])) { id =>
                        complete(books.find(_.id == id))
                    } ~
                        pathEndOrSingleSlash {
                            complete(books)
                        }
                } ~
                post {
                    entity(as[Book]) { book =>
                        books = books :+ book
                        complete(StatusCodes.OK)
                    } ~
                        complete(StatusCodes.BadRequest)
                }
        }
}
