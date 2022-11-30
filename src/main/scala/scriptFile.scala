package old.actors

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Terminated}

abstract class Msg
case class Info(msg:String) extends Msg
case class NewMessage(from:String,msg:String) extends Msg
case class Send(msg:String) extends Msg
case class Connect(username:String) extends Msg
case class BroadCast(msg:String) extends Msg
case object Disconnect
class Server extends Actor {

  var clients = List[(String,ActorRef)]()
  override def receive: Receive = {
    case Connect(username) => {
      broadcast(Info(s"$username has joined the chat"))
      clients = (username,sender) :: clients
      context.watch(sender)

    }
    case BroadCast(message) => {
      val clientOption = clients.find(_._2 == sender)
      if(clientOption.isDefined) {
        val username = clientOption.get._1
        broadcast(NewMessage(username,message))
      }
    }
    case Terminated(client) => {
      val clientOption = clients.find(_._2 == sender)
      clients = clients.filterNot(_._2 == client)
      if(clientOption.isDefined) {
        val username = clientOption.get._1
        broadcast(Info(s"$username has left chat"))
      }
    }
  }

  def broadcast(info:Msg) = {
    clients.foreach(_._2 ! info)
  }
}
class Client(username:String,server:ActorRef) extends Actor {
  server ! Connect(username)
  override def receive = {
    case info:Info => {
      println(s"[$username's client]- ${info.msg}")
    }
    case send: Send => {
      server ! BroadCast(send.msg)
    }
    case newMsg:NewMessage => {
      println(s"[$username's client]- from = ${newMsg.from},message = ${newMsg.msg}")
    }
    case Disconnect => self ! PoisonPill
  }
}

object BroadCastChat extends App {

  val system = ActorSystem("Broadcaster")

  val server = system.actorOf(Props[Server],"Server")

  val client1 = system.actorOf(Props(new Client("aamir",server)),"Client1")
  val client2 = system.actorOf(Props(new Client("oby",server)),"Client2")

  Thread.sleep(300)

  client2 ! Send("Hi all")

  Thread.sleep(300)
  val client3 = system.actorOf(Props(new Client("faik",server)),"Client3")
  val client4 = system.actorOf(Props(new Client("rafaan",server)),"Client4")

  Thread.sleep(1000)

   client1 ! Send("lechmovo sarneee")
  Thread.sleep(1000)

  client4 ! Disconnect
}

/**
 * [aamir's client]- from = aamir,message = lechmovo sarneee
 * if i braodcaet message, i shouldn't receive it.
 */