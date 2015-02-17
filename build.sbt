import AssemblyKeys._

assemblySettings

name := "jamr"

version := "0.1-SNAPSHOT"

organization := "edu.cmu.lti.nlp"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
//  "edu.stanford.nlp" % "stanford-corenlp" % "3.3.1",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.5.0" classifier "models",
  "com.googlecode.lanterna" % "lanterna" % "2.1.8",
  "com.esotericsoftware.kryo" % "kryo" % "2.24.0",
  "com.google.protobuf" % "protobuf-java" % "2.5.0",
  "com.pholser" % "junit-quickcheck-core" % "0.4-beta-3",
  "org.slf4j" % "slf4j-api" % "1.7.7"
//  "org.scala-lang" % "scala-swing" % "2.10.3"
)

//scalaSource in compile := (baseDirectory in compile).value  / "src"

scalaSource in Compile := baseDirectory.value / "src"

// Running JAMR via sbt:

fork in run := true  // run in separate JVM than sbt

connectInput in run := true

outputStrategy in run := Some(StdoutOutput)  // connect to stdin/stdout/stderr of sbt's process

logLevel in run := Level.Error  // don't clutter stdout with [info]s

ivyLoggingLevel in run := UpdateLogging.Quiet

traceLevel in run := 0

mergeStrategy in assembly := {
  case PathList("edu", "stanford", "nlp", "util", "OneToOneMap.class") => MergeStrategy.first
  case PathList("src", "edu", "stanford", "nlp", "util", "OneToOneMap.java") => MergeStrategy.first
  case PathList("META-INF", "maven", "org.antlr", "antlr-runtime", "pom.properties") => MergeStrategy.first
  case PathList("META-INF", "maven", "org.antlr", "antlr-runtime", "pom.xml") => MergeStrategy.first
  case PathList("META-INF", "maven", "de.jollyday", xs @ _*) => MergeStrategy.first
  case PathList("org", "antlr", "runtime", xs @ _*) => MergeStrategy.first
  case PathList("com", "google", "protobuf", xs @ _*) => MergeStrategy.first
  case PathList("edu", "berkeley", "nlp", xs @ _*) => MergeStrategy.first
  case PathList("java_cup", xs @ _*) => MergeStrategy.first
  case PathList("junit", xs @ _*) => MergeStrategy.first
  case PathList("org", "ejml", xs @ _*) => MergeStrategy.first
  case PathList("org", "hamcrest", xs @ _*) => MergeStrategy.first
  case PathList("org", "junit", xs @ _*) => MergeStrategy.first
  case x => (mergeStrategy in assembly).value(x)
}

javaOptions in run ++= Seq(
  "-Xmx8g",
  "-XX:MaxPermSize=256m",
  "-ea",
  "-Dfile.encoding=UTF-8",
  "-XX:ParallelGCThreads=2"
)

mainClass := Some("edu.cmu.lti.nlp.amr.AMRParser")
