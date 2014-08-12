name := "CFPaxos"

version := "0.1"

scalaVersion := "2.10.4"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.4",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.4",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.4",
  "org.scalatest" %% "scalatest" % "2.0" % "test"
)

org.scalastyle.sbt.ScalastylePlugin.Settings
