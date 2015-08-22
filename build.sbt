name := "CFABCast"

organization := "cfabcast"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.12",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.12",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.12",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.12",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.12",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

org.scalastyle.sbt.ScalastylePlugin.Settings

publishMavenStyle := true

publishArtifact in (Compile, packageBin) := true

publishArtifact in (Compile, packageSrc) := false

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Test, packageBin) := false

publishTo <<= version { (v: String) =>
  if (v.trim.endsWith("-SNAPSHOT"))
    Some(Resolver.file("Snapshots", file("../maven-repo/snapshots/")))
  else
    Some(Resolver.file("Releases", file("../maven-repo/releases/")))
}

