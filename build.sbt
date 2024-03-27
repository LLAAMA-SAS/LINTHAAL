import BuildHelper.*
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import Dependencies.*

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(List(organization := "org.linthaal", name := "Linthaal", startYear := Some(2023), version := "1.3.0", scalaVersion := "3.3.1", fork := true, dynverSeparator := "-"))

lazy val root =
  project
    .in(file("."))
    .enablePlugins(JavaAppPackaging, DockerPlugin, DockerSpotifyClientPlugin)
    .settings(scalaVersion := "3.3.1")
    .settings(scalacOptions := stdOptions)
    .settings(
      libraryDependencies ++= Seq(
        pekkoActor,
        pekkoStream,
        pekkoHttp,
        pekkoSprayJson,
        pekkoHttpXml,
        logbackClassic,
        scalaUri,
        commonsCodec,
        neo4jDriver,
        pekkoActorTestkit,
        scalaTest,
        scopt))
    .settings(run / fork := true)
    .settings(Compile / mainClass := Some("org.linthaal.Linthaal"))
    .settings(Universal / mappings ++= directory("user"))
    .settings(Universal / javaOptions ++= Seq("-J-Xmx1G", "-J-Xms256m"))
    .settings(dockerSettings)
