import Dependencies.Versions.*
import sbt.*

object Dependencies {

  object Versions {
    lazy val akkaVersion = "2.9.3"
    lazy val akkaHttpVersion = "10.6.3"
  }

  val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.16" % Test

  val akkaActor: ModuleID = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  val akkaStream: ModuleID = "com.typesafe.akka" %% "akka-stream" % akkaVersion

  val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
  val akkaHttpXml: ModuleID = "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion

  val akkaActorTestkit: ModuleID = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test

  val logbackClassic: ModuleID = "ch.qos.logback" % "logback-classic" % "1.4.7"

  val scalaUri: ModuleID = "io.lemonlabs" %% "scala-uri" % "4.0.3"

  val commonsCodec: ModuleID = "commons-codec" % "commons-codec" % "1.16.0"

  val neo4jDriver: ModuleID = "org.neo4j.driver" % "neo4j-java-driver" % "5.7.0"

  val upickle: ModuleID = "com.lihaoyi" % "upickle_3" % "3.2.0"

  // google ai
  val googleAIPlatform: ModuleID = "com.google.cloud" % "google-cloud-aiplatform" % "3.47.0"

  val googleVertexAI: ModuleID = "com.google.cloud" % "google-cloud-vertexai" % "1.6.0"

  val googleProtobufJavaUtil: ModuleID = "com.google.protobuf" % "protobuf-java-util" % "3.25.3"

  val googleProtobufJava: ModuleID = "com.google.protobuf" % "protobuf-java" % "3.25.3"

  val linthaalDeps = Seq(
    akkaActor,
    akkaStream,
    akkaHttp,
    akkaSprayJson,
    akkaHttpXml,
    logbackClassic,
    scalaUri,
    commonsCodec,
    neo4jDriver,
    akkaActorTestkit,
    scalaTest,
    upickle,
    googleAIPlatform,
    googleVertexAI,
    googleProtobufJavaUtil,
    googleProtobufJava
  )
}
