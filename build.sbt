name := "linthaal"

version := "1.0"

scalaVersion := "3.3.0"

crossScalaVersions := Seq(scalaVersion.value, "2.13.11")

lazy val akkaVersion = "2.8.2"
val akkaHttpVersion = "10.5.2"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,

  "ch.qos.logback" % "logback-classic" % "1.2.11",

  "io.lemonlabs" %% "scala-uri" % "4.0.3",

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.16" % Test
)
