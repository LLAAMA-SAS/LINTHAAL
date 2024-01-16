import com.typesafe.sbt.packager.Keys.{ dockerBaseImage, dockerBuildOptions, dockerExecCommand }
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
import sbt.Keys.version

object BuildHelper {

  val stdOptions: List[String] = List("-deprecation", "-feature", "-unchecked")

  val dockerSettings = List(
    dockerBaseImage := "docker.io/library/eclipse-temurin:17-jre-alpine",
    dockerUpdateLatest := true,
    dockerExposedPorts := Seq(7847),
    dockerBuildCommand := {
      if (sys.props("os.arch") != "amd64") {
        dockerExecCommand.value ++ Seq("buildx", "build", "--platform=linux/amd64", "--load") ++ dockerBuildOptions.value :+ "."
      } else dockerBuildCommand.value
    },
    dockerRepository := Some("docker.io"),
    dockerAlias := DockerAlias(dockerRepository.value, Some("llaamasas"), "linthaal", Some(version.value)))
}
