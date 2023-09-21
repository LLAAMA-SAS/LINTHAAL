import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*


enablePlugins(JavaAppPackaging, DockerPlugin, DockerSpotifyClientPlugin)

organization := "io.linthaal"
name := "linthaal"

version := "1.0.0"

scalaVersion := "3.3.0"

//crossScalaVersions := Seq(scalaVersion.value, "2.13.11")

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

  "ch.qos.logback" % "logback-classic" % "1.4.7",

  "io.lemonlabs" %% "scala-uri" % "4.0.3",

  // apache common codec (hex, base64, etc.)
  "commons-codec" % "commons-codec" % "1.16.0",

  // Swagger API publication
//  "io.swagger" % "swagger-jaxrs" % "1.6.2",
//  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.10.0",

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.16" % Test
)

ThisBuild / dynverSeparator := "-"
run / fork := true

Compile / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked")

Compile / mainClass := Some("org.linthaal.Linthaal")

Universal / mappings ++= {
  directory("user")
}

Universal / javaOptions ++= Seq(
  // -J params will be added as jvm parameters
  "-J-Xmx1G",
  "-J-Xms256m"

  //   others will be added as app parameters
  // should be given as variable by docker run

  // you can access any build setting/task here
  //  s"-version=${version.value}"
)

dockerBaseImage := "docker.io/library/adoptopenjdk:12-jre-hotspot"

dockerUpdateLatest := true

dockerExposedPorts := Seq(9000)

dockerBuildCommand := {
  if (sys.props("os.arch") != "amd64") {
    // use buildx with platform to build supported amd64 images on other CPU architectures
    // this may require that you have first run 'docker buildx create' to set docker buildx up
    dockerExecCommand.value ++ Seq("buildx", "build", "--platform=linux/amd64", "--load") ++ dockerBuildOptions.value :+ "."
  } else dockerBuildCommand.value
}

dockerRepository := Some("docker.io")

dockerAlias := DockerAlias(dockerRepository.value, Some("llaamasas"), "linthaal", Some(version.value))

