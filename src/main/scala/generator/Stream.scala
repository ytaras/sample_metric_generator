package generator

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream._
import akka.stream.scaladsl._
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import scala.concurrent.duration._

/**
  * Created by ytaras on 10/25/16.
  */
object Stream  {
  private def sampleIds(implicit c: Config) = (1 to c.getInt("generator.num-devices")).map(_ => UUID.randomUUID())

  val collectDeviceIds: Flow[UUID, Set[UUID], NotUsed] =
    Flow[UUID]
      .map(x => Set(x))
      .reduce( _ ++_ )

  def generateMeasures(distruptor: Measure => String): Flow[Set[UUID], String, NotUsed] =
    Flow[Set[UUID]]
      .named("generate_metrics")
      .mapConcat(_.map(Measure.sample))
      .map(distruptor)

  def tickedMeasuresActor(implicit as: ActorSystem, c: Config) = Source
      .actorRef[Measure](c.getInt("generator.num-devices") * 100, OverflowStrategy.fail)
      .mapMaterializedValue(startDevices)

  def startDevices(publishTo: ActorRef)(implicit as: ActorSystem, c: Config) = {
    val deviceSupervisor = as.actorOf(
      Props(classOf[DeviceActorSupervisor], c.getDuration("generator.frequency").toMillis.millis, publishTo),
      "device_supervisor"
    )
    sampleIds.foreach { x => deviceSupervisor ! DeviceActorSupervisorApi.RegisterDevice(x) }
  }

  def main(args: Array[String]): Unit = {
    Kamon.start()
    val measuresCounter = Kamon.metrics.counter("measures-counter")
    implicit val config = ConfigFactory.load()
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

