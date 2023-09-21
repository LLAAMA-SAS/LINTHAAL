logLevel := Level.Warn

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.4") // packaging for docker
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")   // dynanmic versioning: https://github.com/sbt/sbt-dynver
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2") // scala code formating

//The Docker-spotify client is a provided dependency.
// You have to explicitly add it on your own.
// It brings a lot of dependencies that could slow your build times.
// This is the reason the dependency is marked as provided.
libraryDependencies += "com.spotify" % "docker-client" % "8.16.0"
