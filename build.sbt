import sbtassembly.MergeStrategy
import sbtassembly.MergeStrategy.defaultMergeStrategy

name := "CFABCast"

organization := "cfabcast"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.7"

val akkaVersion = "2.3.14"

val kamonVersion = "0.5.1"

scalacOptions in Global ++= Seq(
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
  "-language:_",
  "-target:jvm-1.7"
)

//production
//javaOptions in run ++= Seq("-Xms3g", "-Xmx3g", "-XX:+CMSClassUnloadingEnabled", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=3000")

//debug
javaOptions in run ++= Seq("-Xms512M", "-Xmx512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled", "-XX:MaxPermSize=256M", "-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps", "-XX:-HeapDumpOnOutOfMemoryError", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=3000")

//TODO: update to jre 1.8
javacOptions ++= Seq(
  "-source", "1.7",
  "-target", "1.7",
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)
resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Kamon Repository Snapshots"  at "http://snapshots.kamon.io"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.3.3",
  "org.scala-lang.modules" %%	"scala-async" % "0.9.5", 
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

/* Scala Style plugin */
org.scalastyle.sbt.ScalastylePlugin.Settings

// We need to ensure that the JVM is forked for the
// AspectJ Weaver to kick in properly and do it's magic.
fork in run := true


/* SBT Assembly merge strategy */
// Disable tests in assembly
test in assembly := {}

// Create a new MergeStrategy for aop.xml files
// Taken from: https://gist.github.com/colestanfield/fac042d3108b0c06e952
val aopMerge: MergeStrategy = new MergeStrategy {
  val name = "aopMerge"
  import scala.xml._
  import scala.xml.dtd._
  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val dt = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
    val file = MergeStrategy.createMergeTarget(tempDir, path)
    val xmls: Seq[Elem] = files.map(XML.loadFile)
    val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
    val weaverChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
    val options: String = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
    val weaverAttr = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
    val aspects = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
    val weaver = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
    val aspectj = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
    XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}

// Use defaultMergeStrategy with a case for aop.xml
// I like this better than the inline version mentioned in assembly's README
val customMergeStrategy: String => MergeStrategy = {
  case PathList("META-INF", "aop.xml") =>
    aopMerge
  case s =>
    defaultMergeStrategy(s)
}

// Use the customMergeStrategy in your settings
assemblyMergeStrategy in assembly := customMergeStrategy

/* SBT publish */
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

