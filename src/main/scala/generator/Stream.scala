package generator

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.serialization.{NullSerializer, ByteArraySerializer}
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import scala.collection.immutable.Seq
import scala.concurrent.duration._

/**
  * Created by ytaras on 10/25/16.
  */
object Stream extends StreamUtils {
  import StreamSyntax._
  private val sampleIds = (0 to 500).map(_ => UUID.randomUUID())

  val initialLoadDeviceIds: Source[UUID, NotUsed] = Source(sampleIds).named("starting_device_ids")

  val collectDeviceIds: Flow[UUID, Set[UUID], NotUsed] =
    Flow[UUID]
      .map(x => Set(x))
      .reduce( _ ++_ )

  val tick: Source[Unit, Cancellable] = Source.tick(0.second, 250.millis, ())

  def generateMeasures(distruptor: Measure => String): Flow[Set[UUID], String, NotUsed] =
    Flow[Set[UUID]]
      .named("generate_metrics")
      .mapConcat(_.map(Measure.sample))
      .map(distruptor)

  def main(args: Array[String]): Unit = {
    Kamon.start()
    val measuresCounter = Kamon.metrics.counter("measures-counter")
    val config = ConfigFactory.load()
    val disruptor = MessageDisruptor(config)
    val outputTo = config.getString("generator.topic.measures")
    implicit val as = ActorSystem()
    implicit val am = ActorMaterializer()
    val producerSettings: ProducerSettings[String, String] =
      ProducerSettings(system = as,
        keySerializer = new StringSerializer(),
        valueSerializer = new StringSerializer)
      initialLoadDeviceIds.via(collectDeviceIds)
        .wontFinish
        .holdWithWait
        .zip(tick)
        .map(_._1)
        .via(generateMeasures(disruptor))
        .map(elem => new ProducerRecord[String, String](outputTo, elem))
        .alsoTo(
          Sink.foreach(_ => measuresCounter.increment())
        )
        .runWith(Producer.plainSink(producerSettings).named("kafka-writer"))
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