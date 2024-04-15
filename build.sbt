name := "distcomputing"

version := "1.0"

scalaVersion := s"2.13.12"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val akkaVersion = "2.9.1"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

lazy val app = project in file(".") enablePlugins (Cinnamon)
run / cinnamon := true
test / cinnamon := true
cinnamonLogLevel := "INFO"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.12" % Test,
  "com.typesafe.play" %% "play-json" % "2.10.4",
  Cinnamon.library.cinnamonAkka,
  Cinnamon.library.cinnamonAkkaTyped,
  Cinnamon.library.cinnamonPrometheus,
  Cinnamon.library.cinnamonPrometheusHttpServer,
)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

