package generator

import java.util.UUID

import akka.actor.{ActorLogging, Props, Actor, ActorRef}
import akka.pattern.BackoffSupervisor
import generator.DeviceActorSupervisorApi.RegisterDevice
import scala.concurrent.duration._

/**
  * Created by ytaras on 10/27/16.
  */
class DeviceActor(deviceId: UUID, duration: FiniteDuration, publishTo: ActorRef) extends Actor with ActorLogging {
  import DeviceActorApi._

  import context.dispatcher

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.system.scheduler.schedule(0.millis, duration, self, Tick)
  }

  override def receive: Receive = {
    case Tick =>
      log.debug("measuring device {}", deviceId)
      Measure.allMeasures(deviceId).foreach { publishTo ! _ }
  }
}

object DeviceActorApi {
  case object Tick
  case object Started
}

class DeviceActorSupervisor(duration: FiniteDuration, publishTo: ActorRef) extends Actor with ActorLogging {
  def startDevice(uuid: UUID) = {
    log.debug("starting device {}", uuid)
    val childProps = Props(classOf[DeviceActor], uuid, duration, publishTo)
    context.actorOf(BackoffSupervisor.props(
      childProps,
      s"device_$uuid",
      minBackoff = 50.millis, maxBackoff = 100.millis, randomFactor = 0.2
    ))
  }

  override def receive: Actor.Receive = {
    case RegisterDevice(uuid) => startDevice(uuid)
  }
}

object DeviceActorSupervisorApi {
  case class RegisterDevice(uuid: UUID)
}
