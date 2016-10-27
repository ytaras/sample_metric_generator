name := "sample_metric_generator"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaVersion = "2.4.11"
  Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % "0.11-RC1",
    "org.slf4j" % "slf4j-simple" % "1.7.21",
    "org.scalacheck" %% "scalacheck" % "1.13.3",
    "io.kamon" %% "kamon-core" % "0.6.0",
    "io.kamon" %% "kamon-jmx" % "0.6.0",
    "io.kamon" %% "kamon-log-reporter" % "0.6.0",
    "io.kamon" %% "kamon-akka" % "0.6.0",
    "io.kamon" %% "kamon-scala" % "0.6.0",
    "io.kamon" %% "kamon-system-metrics" % "0.6.0",
    "org.specs2" %% "specs2-core" % "3.8.5" % "test"
  )
}

scalacOptions in Test ++= Seq("-Yrangepos")

aspectjSettings

// Here we are effectively adding the `-javaagent` JVM startup
// option with the location of the AspectJ Weaver provided by
// the sbt-aspectj plugin.
javaOptions in run <++= AspectjKeys.weaverOptions in Aspectj

// We need to ensure that the JVM is forked for the
// AspectJ Weaver to kick in properly and do it's magic.
fork in run := true