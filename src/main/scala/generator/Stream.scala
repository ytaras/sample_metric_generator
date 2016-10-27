package generator

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream._
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.duration._

/**
  * Created by ytaras on 10/25/16.
  */
object Stream  {
  import StreamSyntax._
  private val sampleIds = (1 to 50000).map(_ => UUID.randomUUID())

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

  def tickedMeasuresActor(implicit as: ActorSystem) = Source
      .actorRef[Measure](500000, OverflowStrategy.fail)
      .mapMaterializedValue(startDevices)

  def tickedMeasuresStream = initialLoadDeviceIds.via(collectDeviceIds)
      .wontFinish
      .holdWithWait
      .zip(tick)
      .map(_._1)
      .mapConcat(_.map(Measure.sample))

  def startDevices(publishTo: ActorRef)(implicit as: ActorSystem) = {
    val deviceSupervisor = as.actorOf(
      Props(classOf[DeviceActorSupervisor], 500.millis, publishTo),
      "device_supervisor"
    )
    sampleIds.foreach { x => deviceSupervisor ! DeviceActorSupervisorApi.RegisterDevice(x) }
  }

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
    tickedMeasuresActor
      .map(disruptor)
      .map(elem => new ProducerRecord[String, String](outputTo, elem))
      .alsoTo(
        Sink.foreach(_ => measuresCounter.increment())
      ).runWith(Producer.plainSink(producerSettings).named("kafka-writer"))
  }

}

