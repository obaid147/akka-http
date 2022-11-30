package part1_recap

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

object AkkaStreamsRecap extends App {
  // Akka HTTP is based on Akka streams
  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // an object that allocates right resources for constructing akka streams component.

  val source = Source(1 to 100) // source will publish numbers from 1 to 100.
  val flow = Flow[Int].map(x => x + 1) // flow will magnify those numbers by 1.
  val sink = Sink.foreach[Int](println) // source elements will flow to sink and gets printed to console

  val runnableGraph = source.via(flow).to(sink)
  runnableGraph//.run()// This a Materialized value
  // The run method take implicit materializer and materializer will allocate right resources to start akka stream


  // Materialized value is obtained by calling run method on a runnable graph. using Materialization
  val materializedValue = runnableGraph//.run() // NotUsed == Unit...

  // Materialized value
  val sumSink = Sink.fold[Int, Int](0)((x, y) => x + y) // compute total sum of elements that will go inside sink.
  // sumSink is a Sink[Int, Future[Int]].. Int goes into the sink and Future[Int] is exposed by the sink.........

  /*val sumFuture: Future[Int] = source.runWith(sumSink) // runWith connects a source with a sink and runs it.
  import system.dispatcher
  sumFuture.onComplete{
    case Success(value) => println(s"SUM = $value")
    case Failure(exception) => println(s"$exception")
  }*/

  val anotherMaterializedValue = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.left)//.run()
  // keep.right will take flow's materialized value instead of sources materialized value
  // and between source.viaMat(flow)(Keep.right) and sink materialized value, we choose Keep.left which means
  // take the materialized value of source.viaMat(flow)(Keep.right) ==== flow's materialized value.

  /*
  * #1 materializing a graph means Materializing all the components.
  * #2 materialized value can be anything at all.
  */

  // BackPressure actions...
  /*
  * buffer elements
  * apply a strategy in case buffer overflows. (choose to drop elements, drop entire buffer) or
  * fail the entire stream
  */

  val bufferedFlow = Flow[Int].buffer(10, OverflowStrategy.dropHead)
  /* In response of the slow demand of the consumer of this flow. The flow will attempt to buffer
  *  rapidly incoming elements from upstream and if the buffer overflows this dropHead strategy will be applied to buffer.
  */
  source.async // meaning this component will run on its own actor
    .via(bufferedFlow).async // this component will also run on separate actor
    .runForeach { x =>
      // a slow consumer
      Thread.sleep(100)
      println(x)
    }
  /**
  because this flow is slow, bufferedFlow will receive a backpressure signal because consumer cannot process
  the elements fast enough and in response to backpressure, this bufferedFlow will attempt to buffer incoming
  elements from the source.
  Because we limited the buffer and it only contains 10 elements which is the size of buffer....

   Source is very fast and elements will be buffered, dropHead strategy will be applied meaning that the oldest
   element of buffer will be dropped to make room for new incoming element.
  */
  /* output
  * 1 to 16 coz they were buffered at source
  * 91 to 100 offered by the bufferedFlow and only these end up in sink coz all the intermediate elements
  * are being dropped by OverflowStrategy dropHead
  * */
}
