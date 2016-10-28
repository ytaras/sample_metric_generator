package generator

import java.util.UUID

import akka.actor.{ActorLogging, Props, Actor, ActorRef}
import akka.pattern.BackoffSupervisor
import generator.AgentActorSupervisorApi.RegisterAgents
import scala.concurrent.duration._

/**
  * Created by ytaras on 10/27/16.
  */
class AgentActor(agentId: UUID, duration: FiniteDuration, publishTo: ActorRef) extends Actor with ActorLogging {
  import AgentActorApi._

  import context.dispatcher

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.system.scheduler.schedule(0.millis, duration, self, Tick)
  }

  override def receive: Receive = {
    case Tick =>
      log.debug("measuring device {}", agentId)
      Measure.allMeasures(agentId).foreach { publishTo ! _ }
  }
}

object AgentActorApi {
  case object Tick
  case object Started
}

class AgentActorSupervisor(duration: FiniteDuration, publishTo: ActorRef) extends Actor with ActorLogging {
  def startDevice(uuid: UUID) = {
    log.debug("starting device {}", uuid)
    val childProps = Props(classOf[AgentActor], uuid, duration, publishTo)
    context.actorOf(BackoffSupervisor.props(
      childProps,
      s"device_$uuid",
      minBackoff = 50.millis, maxBackoff = 100.millis, randomFactor = 0.2
    ))
  }

  override def receive: Actor.Receive = {
    case RegisterAgents(uuid) => startDevice(uuid)
  }
}

object AgentActorSupervisorApi {
  case class RegisterAgents(uuid: UUID)
}
