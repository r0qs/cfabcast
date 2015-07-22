name := "CFABCast"

version := "0.1"

scalaVersion := "2.11.6"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.11",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.11",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.11",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

org.scalastyle.sbt.ScalastylePlugin.Settings
