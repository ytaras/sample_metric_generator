package generator

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
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
object Stream  {
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

