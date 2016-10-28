package generator

import java.util.{Random, UUID}
import org.scalacheck.Gen

import generator.MetricKey.MetricKey

import scala.annotation.tailrec

/**
  * Created by ytaras on 10/25/16.
  */
case class Measure(
                    agentId: UUID,
                    metricKey: MetricKey,
                    metricValue: Double,
                    timestamp: Long
                  ) {

  def delayed(amount: Int) = copy(timestamp = timestamp - amount)

  def toValidJson =
    s"""{ "agent_id": "$agentId", "metric_key": "$metricKey", "metric_value": $metricValue, "timestamp": $timestamp }"""

  def toBrokenJson =
    s"""{ "agent_id": "$agentId" "metric_key" "$metricKey", "metric_value: $metricValue, "timestamp": $timestamp """

  def toJsonWithMissingKey =
    s"""{ "agent_id": "$agentId", "metric_value": $metricValue, "timestamp": $timestamp }"""

}

object Measure extends MeasureGenerators {

  def sample(agentId: UUID): Measure =
    generateSample(validMeasureGenerator(agentId))

  @tailrec
  def generateSample[T](g: Gen[T]): T = {
    g.sample match {
      case None => generateSample(g)
      case Some(x) => x
    }
  }

  def allMeasures(agentId: UUID): Seq[Measure] = {
    MetricKey.values.toSeq.map { key =>
      val data = generateSample(metricValueGenerator(key))
      Measure(agentId, key, data, System.currentTimeMillis())
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

  def validMeasureGenerator(agentId: UUID): Gen[Measure] = for {
    metric <- keyGenerator
    value <- metricValueGenerator(metric)
  } yield Measure(
    agentId, metric, value, System.currentTimeMillis()
  )

}
