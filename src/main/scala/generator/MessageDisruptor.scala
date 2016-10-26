package generator

import com.typesafe.config.Config

import scala.util.Random

/**
  * Created by ytaras on 10/26/16.
  */
object MessageDisruptor {
  def apply(config: Config): (Measure => String) = {
    breakJson(config.getDouble("generator.probabilities.broken_json")) orElse
      removeKey(config.getDouble("generator.probabilities.missing_key")) orElse
      lateEvent(config.getDouble("generator.probabilities.late_event"), config.getInt("generator.late_event.max")) orElse
      renderCorrect
  }

  private def breakJson(brokenProb: Double): PartialFunction[Measure, String] = {
    case x if Random.nextDouble() <= brokenProb => x.toBrokenJson
  }

  private def removeKey(missingProb: Double): PartialFunction[Measure, String] = {
    case x if Random.nextDouble() <= missingProb => x.toJsonWithMissingKey
  }
  private def lateEvent(brokenProb: Double, max: Int): PartialFunction[Measure, String] = {
    case x if Random.nextDouble() <= brokenProb => x.delayed(Random.nextInt(max)).toValidJson
  }
  private val renderCorrect: PartialFunction[Measure, String] = {
    case x => x.toValidJson
  }
}
