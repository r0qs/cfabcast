name := "LibConsensus"

version := "0.1"

scalaVersion := "2.10.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.1",
  "com.typesafe.akka" %% "akka-remote" % "2.2.1",
  "com.typesafe.akka" %% "akka-kernel" % "2.2.1"
)
