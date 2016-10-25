package generator

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage.GraphStage
import scala.collection.immutable.{Iterable, Seq}
import scala.concurrent.duration._

/**
  * Created by ytaras on 10/25/16.
  */
object Stream extends StreamUtils {
  private val sampleIds = (0 to 5).map(_ => UUID.randomUUID())

  def initialLoadDeviceIds: Source[UUID, NotUsed] = Source(sampleIds)

  def collectDeviceIds: Flow[UUID, Set[UUID], NotUsed] =
    Flow[UUID].conflateWithSeed(x => Set(x))((s, u) => s + u)

  def tick: Source[Unit, Cancellable] = Source.tick(0.second, 1.second, ())

  def repeatOnTick(data: Source[Set[UUID], _], tick: Source[Unit, _]): Source[Set[UUID], _] = {
    val ds: Source[Either[Set[UUID], Unit], _] = data.map { x => Left(x) }
    val ts: Source[Either[Set[UUID], Unit], _] = tick.map { _ => Right(()) }
    ds.merge(ts).statefulMapConcat {  () =>
      var state: Set[UUID] = Set.empty[UUID]
      ev => ev match {
        case Left(s) =>
          state = s
          Seq(state)
        case Right(()) =>
          Seq(state)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val as = ActorSystem()
    implicit val am = ActorMaterializer()
    repeatOnTick(
      initialLoadDeviceIds.via(collectDeviceIds), tick)
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