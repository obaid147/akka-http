package part1_recap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScalaRecap extends App {

  val f: Future[Int] = Future {
    10
  } // 2nd arg is required as an implicit ExecutionContext, as we imported it, the compiler injected  it here...

  f.onComplete {
    case Success(value) => println(s"success $value")
    case Failure(ex) => println(s"Failure reason:- $ex")
  }

  val pf: PartialFunction[Int, Int] = {
    case 1 => 1
    case 2 => 2
    case _ => 0
  }
  println(pf(2))

  // type alise
  type AkkaReceive = PartialFunction[Any, Unit]

  def receive: AkkaReceive = {
    case 1 => println("hey...")
    case _ => println("anything...")
  }

  receive(1)

  // implicits
  implicit val timeout: Int = 3000

  def setTimeout(f: () => Unit)(implicit timeout: Int): Unit = f()
  //  setTimeout(() => println("Timeout"))(timeout)
  setTimeout(() => println("Timeout"))
  // no need to supply other arg list as it is injected by the compiler
  // because 2nd arg is implicit, compiler looks for it.


  // implicit conversions ----------------------------------------------
  // 1. Implicit Methods
  case class Person(name: String) {
    def greet: String = s"Hi, my name is $name"
  }

  implicit def fromStringToPerson(string: String): Person = Person(string)

  "Person1".greet
  // The compiler automatically calls the fromStringToPerson method on the "Person1" string,
  // It transforms the string to a Person and the calls the greet method on it........
  // equivalent to ===== fromStringToPerson("Person1").greet // This is what compiler does.


  // 2. Implicit Classes -----
  implicit class Dog(name: String) {
    def bark = println("bark!")
  }
  "MyDogName".bark
  // The compiler constructs a new Dog from "MyDogName" string coz its an implicit class.
  // Then it can call bark method on the resulting object.
  // equivalent to ===== new Dog("MyDogName").bark // This is what compiler does.


  // Implicit Organizations

  /*1... Implicits are fetched from local scope*/
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  println(List(1, 2, 3).sorted) // compiler automatically inject numberOrdering as sorted has 2nd param implicit.
  // List(3, 2, 1)

  /*2... Imported Scope*/
  val future: Future[Int] = Future(100) // import scala.concurrent.ExecutionContext.Implicits.global

  /*3... Companion Object of the types involved in the call*/
  object Person{
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }
  List(Person("ABC"), Person("DEF")).sorted // inject
  // the compiler has found an implicit value for ordering of person it will inject here.
  // inject -> Person.personOrdering as the 2nd arg list for sorted
  // result -> List(Person("ABC"), Person("DEF))


}
