package generator

import java.util.{Random, UUID}
import org.scalacheck.Gen

import generator.MetricKey.MetricKey

import scala.annotation.tailrec

/**
  * Created by ytaras on 10/25/16.
  */
case class Measure(
                  deviceId: UUID,
                  metricKey: MetricKey,
                  metricValue: Double,
                  timestamp: Long
                  ) {

  def delayed(amount: Int) = copy(timestamp = timestamp - amount)

  def toValidJson =
    s"""{ "device_id": "$deviceId", "metric_key": "$metricKey", "metric_value": $metricValue, "timestamp": $timestamp }"""

  def toBrokenJson =
    s"""{ "device_id": "$deviceId" "metric_key" "$metricKey", "metric_value: $metricValue, "timestamp": $timestamp """

  def toJsonWithMissingKey =
    s"""{ "device_id": "$deviceId", "metric_value": $metricValue, "timestamp": $timestamp }"""

  def toSomeJson = {
    scala.util.Random.nextInt(15) match {
      case 1 => toBrokenJson
      case 2 => toJsonWithMissingKey
      case _ => toValidJson
    }
    // FIXME - Configure prob
  }
}

object Measure extends MeasureGenerators {

  @tailrec
  def sample(deviceId: UUID): Measure = {
    validMeasureGenerator(deviceId).sample
    match {
      case Some(m) => m
      case None => sample(deviceId)
    }
  }

  def main(args: Array[String]) = {
    (0 to 100).foreach { _ =>
      println(sample(UUID.randomUUID()).toJsonWithMissingKey)
    }
  }

}

trait MeasureGenerators {
  val keyGenerator: Gen[MetricKey] =
    Gen.oneOf(MetricKey.values.toSeq)

  def metricValueGenerator(mk: MetricKey): Gen[Double] = mk match {
    case MetricKey.CpuLoad => normalDistribution(mean = 1.5d, sigma = 1d, max = 5d, min = 0d)
    case MetricKey.FreeMem => normalDistribution(mean = 3.5d, sigma = 5d, max = 15d, min = 0d)
  }

  /* Using Polar form of Box-Muller to generate normal dist from uniform */
  def normalDistribution(mean: Double, sigma: Double, max: Double, min: Double): Gen[Double] = for {
    u1 <- Gen.choose(-1d, 1d)
    u2 <- Gen.choose(-1d, 1d)
    z0 = Math.sqrt(-2d * Math.log(u1)) * Math.cos(Math.PI * 2 * u2)
    res = mean + sigma * z0
    if res <= max && res >= min
  } yield res

  def validMeasureGenerator(deviceId: UUID): Gen[Measure] = for {
    metric <- keyGenerator
    value <- metricValueGenerator(metric)
  } yield Measure(
    deviceId, metric, value, System.currentTimeMillis()
  )

}
