import BuildHelper.*
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    organization := "org.linthaal",
    name := "Linthaal",
    startYear := Some(2023),
    version := "2.0.0",
    scalaVersion := "3.7.1",
    fork := true,
    dynverSeparator := "-"))

lazy val root =
  project
    .in(file("."))
    .enablePlugins(JavaAppPackaging, DockerPlugin, DockerSpotifyClientPlugin)
    .settings(scalaVersion := "3.7.1")
    .settings(scalacOptions := stdOptions)
    .settings(resolvers ++= ExtResolvers.extRes)
    .settings(libraryDependencies ++=Dependencies.linthaalDeps)
    .settings(run / fork := true)
    .settings(Compile / mainClass := Some("org.linthaal.Linthaal"))
    .settings(Universal / mappings ++= directory("user"))
    .settings(Universal / javaOptions ++= Seq("-J-Xmx1G", "-J-Xms256m"))
    .settings(dockerSettings)
