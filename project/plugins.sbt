logLevel := Level.Warn

resolvers += "Akka library repository".at("https://repo.akka.io/maven") // not same resolver as in build.sbt

addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.0") // packaging for docker
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")   // dynanmic versioning: https://github.com/sbt/sbt-dynver
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2") // scala code formating
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.4.3")
//The Docker-spotify client is a provided dependency.
// You have to explicitly add it on your own.
// It brings a lot of dependencies that could slow your build times.
// This is the reason the dependency is marked as provided.
libraryDependencies += "com.spotify" % "docker-client" % "8.16.0"
