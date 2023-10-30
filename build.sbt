import BuildHelper.*
import BuildHelper.Versions.*
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    organization := "org.linthaal",
    name := "Linthaal",
    startYear := Some(2023),
    version := "1.0.0",
    scalaVersion := "3.3.1",
    fork := true,
    dynverSeparator := "-"))

lazy val root =
  project
    .in(file("."))
    .enablePlugins(JavaAppPackaging, DockerPlugin, DockerSpotifyClientPlugin)
    .settings(scalaVersion := "3.3.1")
    .settings(scalacOptions := stdOptions)
    .settings(libraryDependencies ++= List(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.7",
      "io.lemonlabs" %% "scala-uri" % "4.0.3",
      "commons-codec" % "commons-codec" % "1.16.0",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.16" % Test))
    .settings(run / fork := true)
    .settings(Compile / mainClass := Some("org.linthaal.Linthaal"))
    .settings(Universal / mappings ++= directory("user"))
    .settings(Universal / javaOptions ++= Seq("-J-Xmx1G", "-J-Xms256m"))
    .settings(dockerSettings)
