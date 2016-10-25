name := "sample_metric_generator"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaVersion = "2.4.11"
  Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "org.scalacheck" %% "scalacheck" % "1.13.3",
    "org.specs2" %% "specs2-core" % "3.8.5" % "test"
  )
}

scalacOptions in Test ++= Seq("-Yrangepos")