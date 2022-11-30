package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props, Stash, SupervisorStrategy}
import akka.pattern.ask
import akka.util.Timeout

object AkkaRecap extends App {

  class MyActor extends Actor with ActorLogging with Stash{
    override def receive: Receive = { // message handler
      case "CreateChild" =>
        val child = context.actorOf(Props[ChildActor], "ChildActor")
        child ! "hey child"
      case "stashThis" =>
        stash()
      case "change Handler now" =>
        unstashAll()
        context.become(anotherHandler)
      case "change" => context.become(anotherHandler)
      case message: String => log.info(message)
      case msg => log.info(s"received a message:- $msg")
    }
    def anotherHandler: Receive = {
      case message => log.info(s"received $message")
    }

    override def preStart(): Unit = log.info("I am starting") // works before actor starts its lifecycle

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart // restart the child
      case _ => Stop
    }
  }

  class ChildActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message =>
        log.info(s"$message")
        sender() ! "hey parent"
    }
  }

  // actor encapsulation...
  val system = ActorSystem("AkkaRecap")

  // instantiating an actor through ActorSystem...
  val actor = system.actorOf(Props[MyActor], "actorName")

  // communicating with actor via messages... This is asynchronous message and actor will receive it in Future
  actor ! 1
  actor ! "Welcome!"

  /*
  * messages are sent asynchronously.
  * many actors (in the millions) can share a few dozen threads.
  * each message is handled atomically. No race condition on actors state(never need to lock resources)
  * need for locking disappeared
  * */

  // changing msg handler, context.become + stashing(mixing stash trait)
  // actors can spawn other actors (parent child relation)

  actor ! "CreateChild"

  // Once we create ActorSystem, Akka creates 3 top level parents for us...GUARDIANS
  // guardians:- /system, /user(parent to all actors we create), / == root guardian(parent to every actor in ActorSystem)

  // Actors have defined lifecycle, Actor can be started, stopped, resumed, suspended, restarted...
  // override def preStart .....

  // stopping actors ---> context.stop
  // stopping actors using special messages // PoisonPill, kill
  // actor ! PoisonPill

  // logging can be used by mixing in ActorLogging. log.info,error,warning,debug.... logs message noy printing to STDOut

   /*supervision:-
   How parent actors are going to respond to child actor failures
   each actor has a method called supervisorStrategy
   - When a child actor throws an Exception, Its invariably suspended and its parent actor is notified.
   - supervisorStrategy will kick in and Based on decision cases inside the method, child actor is stopped, restarted...
   */

  // configure infrastructure:- dispatchers, routers, mailboxes.

  // schedulers:- way of something to happen at a defined point in future.
  import system.dispatcher

  import scala.concurrent.duration.DurationInt
  system.scheduler.scheduleOnce(2.seconds){
    actor ! "delayed happy birthday"
  }

  // akka patterns, FSM + askPattern
  implicit val timeout: Timeout = Timeout(7.seconds)
  val future = actor ? "Question" // returns Future
  // use askPattern in conjunction with pipePattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[MyActor], "anotherActor")
  future.mapTo[String].pipeTo(anotherActor).toString // when future completes, its value is sent to this anotherActor

}
