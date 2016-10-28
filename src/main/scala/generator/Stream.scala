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
  val collectAgentIds: Flow[UUID, Set[UUID], NotUsed] =
    Flow[UUID]
      .map(x => Set(x))
      .reduce( _ ++_ )

  def generateMeasures(distruptor: Measure => String): Flow[Set[UUID], String, NotUsed] =
    Flow[Set[UUID]]
      .named("generate_metrics")
      .mapConcat(_.map(Measure.sample))
      .map(distruptor)

  def tickedMeasuresActor(startIds: Seq[UUID])(implicit as: ActorSystem, c: Config) = Source
      .actorRef[Measure](c.getInt("generator.num-agents") * 100, OverflowStrategy.fail)
      .mapMaterializedValue(actor => startDevices(startIds, actor))

  def startDevices(startIds: Seq[UUID], publishTo: ActorRef)(implicit as: ActorSystem, c: Config) = {
    val agentSupervisor = as.actorOf(
      Props(classOf[AgentActorSupervisor], c.getDuration("generator.frequency").toMillis.millis, publishTo),
      "agent_supervisor"
    )
    startIds.foreach { x => agentSupervisor ! AgentActorSupervisorApi.RegisterAgents(x) }
  }

  def main(args: Array[String]): Unit = {
    Kamon.start()
    implicit val config = ConfigFactory.load()
    AgentsRepository.populateAgents(config.getInt("generator.num-agents"))
    val measuresCounter = Kamon.metrics.counter("measures-counter")
    val disruptor = MessageDisruptor(config)
    val outputTo = config.getString("generator.topic.measures")
    implicit val as = ActorSystem()
    implicit val am = ActorMaterializer()

    val producerSettings: ProducerSettings[String, String] =
      ProducerSettings(system = as,
        keySerializer = new StringSerializer(),
        valueSerializer = new StringSerializer)
    tickedMeasuresActor(AgentsRepository.allUuids)
      .map(disruptor)
      .map(elem => new ProducerRecord[String, String](outputTo, elem))
      .alsoTo(
        Sink.foreach(_ => measuresCounter.increment())
      ).runWith(Producer.plainSink(producerSettings).named("kafka-writer"))
  }

}

