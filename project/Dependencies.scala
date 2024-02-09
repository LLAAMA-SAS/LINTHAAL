import Dependencies.Versions.*
import sbt.*

object Dependencies {

  object Versions {
    lazy val pekkoVersion = "1.0.2"
    lazy val pekkoHttpVersion = "1.0.0"
  }

  val pekkoActor: ModuleID = "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion
  val pekkoStream: ModuleID = "org.apache.pekko" %% "pekko-stream" % pekkoVersion
  val pekkoHttp: ModuleID = "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion
  val pekkoSprayJson: ModuleID = "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion
  val pekkoHttpXml: ModuleID = "org.apache.pekko" %% "pekko-http-xml" % pekkoHttpVersion
  val pekkoActorTestkit: ModuleID = "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test
  val logbackClassic: ModuleID = "ch.qos.logback" % "logback-classic" % "1.4.7"
  val scalaUri: ModuleID = "io.lemonlabs" %% "scala-uri" % "4.0.3"
  val commonsCodec: ModuleID = "commons-codec" % "commons-codec" % "1.16.0"
  val neo4jDriver: ModuleID = "org.neo4j.driver" % "neo4j-java-driver" % "5.7.0"
  val milvus: ModuleID = "io.milvus" % "milvus-sdk-java" % "2.3.4"
  val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.16" % Test
}
