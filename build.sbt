name := "CFABCast"

organization := "cfabcast"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-encoding", "UTF-8",
  "-feature",
  "-Xlint",
  "-Xverify",
  "-Xfuture",
  "-Yinline",
  "-Yclosure-elim",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-unused-import",
  "-language:postfixOps",
  "-target:jvm-1.8"
)

//production
javaOptions in run ++= Seq("-Xms512m", "-Xmx2g", "-XX:+CMSClassUnloadingEnabled", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=3000")

//debug
//javaOptions in run += "-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:-HeapDumpOnOutOfMemoryError -XX:+UseG1GC -XX:MaxGCPauseMillis=3000"

javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-Xlint"
)

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.12",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.12",
  "com.typesafe.akka" %% "akka-cluster" % "2.3.12",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.12",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.12",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.12",
  "org.scala-lang.modules" %%	"scala-async" % "0.9.5", 
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

