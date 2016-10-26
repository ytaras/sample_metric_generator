package generator

import akka.stream.{Attributes, Outlet, Inlet, FlowShape}
import akka.stream.scaladsl._
import akka.stream.stage.{OutHandler, InHandler, GraphStageLogic, GraphStage}

/**
  * Created by ytaras on 10/26/16.
  */
object StreamSyntax {
  implicit class SourceSyntax[T, M](s: Source[T, M]) {
    def holdWithWait: Source[T, M] =
      s.via(Flow.fromGraph(new HoldWithWait[T]))

    def wontFinish: Source[T, M] =
    s.via(Flow.fromGraph(new WontFinish[T]))
  }

}

/*
http://doc.akka.io/docs/akka/2.4/scala/stream/stream-cookbook.html#Create_a_stream_processor_that_repeats_the_last_element_seen
 */
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