name := "CFPaxos"

version := "0.1"

scalaVersion := "2.11.6"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.11",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

org.scalastyle.sbt.ScalastylePlugin.Settings
