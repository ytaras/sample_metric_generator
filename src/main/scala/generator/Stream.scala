package generator

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.config.ConfigFactory
import scala.collection.immutable.Seq
import scala.concurrent.duration._

/**
  * Created by ytaras on 10/25/16.
  */
object Stream extends StreamUtils {
  import StreamSyntax._
  private val sampleIds = (0 to 5).map(_ => UUID.randomUUID())

  def initialLoadDeviceIds: Source[UUID, NotUsed] = Source(sampleIds)

  def collectDeviceIds: Flow[UUID, Set[UUID], NotUsed] =
    Flow[UUID]
      .map(x => Set(x))
      .reduce( _ ++_ )

  def tick: Source[Unit, Cancellable] = Source.tick(0.second, 500.millis, ())

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    implicit val as = ActorSystem()
    implicit val am = ActorMaterializer()
      initialLoadDeviceIds.via(collectDeviceIds)
        .wontFinish
        .holdWithWait
        .zip(tick)
        .map(_._1)
        .mapConcat(_.map(Measure.sample))
        .map(_.toSomeJson)
        .runForeach(println)
    Thread.sleep(5000)
    as.terminate()
  }

}

trait StreamUtils {

  def statefulMap[S, I, O](init: S)(f: (I, S) => (O, S)): Flow[I, O, NotUsed] =
    Flow[I].statefulMapConcat(() => {
      var state: S = init
      elem => {
        val (res, newState) = f(elem, state)
        state = newState
        Seq(res)
      }
    })
}

/*
http://doc.akka.io/docs/akka/2.4/scala/stream/stream-cookbook.html#Create_a_stream_processor_that_repeats_the_last_element_seen
 */
object StreamSyntax {
  implicit class SourceSyntax[T, M](s: Source[T, M]) {
    def holdWithWait: Source[T, M] =
      s.via(Flow.fromGraph(new HoldWithWait[T]))

    def wontFinish: Source[T, M] =
    s.via(Flow.fromGraph(new WontFinish[T]))
  }

}
final class HoldWithWait[T] extends GraphStage[FlowShape[T, T]] {
  val in = Inlet[T]("HoldWithWait.in")
  val out = Outlet[T]("HoldWithWait.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var currentValue: T = _
    private var waitingFirstValue = true

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {
        currentValue = grab(in)
        if (waitingFirstValue) {
          waitingFirstValue = false
          if (isAvailable(out)) push(out, currentValue)
        }
        pull(in)
      }

      @throws[Exception](classOf[Exception])
      override def onUpstreamFinish(): Unit = {
        println("finish")
        super.onUpstreamFinish()
      }

      override def onPull(): Unit = {
        if (!waitingFirstValue) push(out, currentValue)
      }
    })

    override def preStart(): Unit = {
      pull(in)
    }
  }
}

final class WontFinish[T] extends GraphStage[FlowShape[T, T]] {
  val out = Outlet[T]("WontFinish.out")
  val in = Inlet[T]("WontFinish.in")

  @throws[Exception](classOf[Exception])
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandlers(in, out, new OutHandler with InHandler {

      var finishd = false

      @throws[Exception](classOf[Exception])
      override def onPull(): Unit = {
        if(!finishd)
          pull(in)
      }

      @throws[Exception](classOf[Exception])
      override def onPush(): Unit = push(out, grab(in))

      @throws[Exception](classOf[Exception])
      override def onUpstreamFinish(): Unit = finishd = true
    })
  }

  override def shape: FlowShape[T, T] = FlowShape.of(in, out)
}