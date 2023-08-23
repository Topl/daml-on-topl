
inThisBuild(
  List(
    organization := "co.topl",
    homepage := Some(url("https://github.com/Topl/daml-bifrost-module")),
    licenses := Seq("MPL2.0" -> url("https://www.mozilla.org/en-US/MPL/2.0/")),
    scalaVersion := "2.13.11",
    testFrameworks += TestFrameworks.MUnit
  )
)

lazy val cleanSrcGen = taskKey[Unit]("Clean DAML Generated Code")

lazy val generateJavaCode = taskKey[Unit]("Build DAML package")


lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-language:higherKinds",
  "-language:postfixOps",
  "-unchecked",
  "-Ywarn-unused:-implicits,-privates",
  "-Yrangepos"
)

lazy val commonSettings = Seq(
  fork := true,
  scalacOptions ++= commonScalacOptions,
  semanticdbEnabled := true, // enable SemanticDB for Scalafix
  Test / testOptions ++= Seq(
    Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "2"),
    Tests.Argument(TestFrameworks.ScalaTest, "-f", "sbttest.log", "-oDGG", "-u", "target/test-reports")
  ),
  resolvers ++= Seq(
    "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/",
    "Sonatype Staging" at "https://s01.oss.sonatype.org/content/repositories/staging",
    "Sonatype Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots/",
    "Sonatype Releases s01" at "https://s01.oss.sonatype.org/content/repositories/releases/",
    "Bintray" at "https://jcenter.bintray.com/"
  ),
  testFrameworks += TestFrameworks.MUnit
)

lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/Topl/daml-bifrost-module")),
  ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  pomExtra :=
    <developers>
      <developer>
        <id>mundacho</id>
        <name>Edmundo Lopez Bobeda</name>
      </developer>
    </developers>
)

lazy val damlToplLib = project
  .in(file("daml-topl-lib"))
  .settings(
    name := "daml-topl-lib",
    commonSettings,
    publishSettings,
    cleanSrcGen := {
          import scala.sys.process._
          val s: TaskStreams = streams.value
          val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
          val cleanSrc: Seq[String] = shell :+ ("rm -rf " + baseDirectory.value + "/src-gen/")
          if((cleanSrc !) == 0) {
            s.log.success("Successfully cleaned src-gen!")
          } else {
            throw new IllegalStateException("src-gen clean up failed!")
          }
    },
    generateJavaCode := {
          import scala.sys.process._
          val s: TaskStreams = streams.value
          val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
          val buildDamlPackage: Seq[String] = shell :+ ("cd " + baseDirectory.value + " && daml build && daml codegen java")
          s.log.info("Generating Java DAML code...")
          if((buildDamlPackage !) == 0) {
            s.log.success("Generation of Java DAML code successful!")
          } else {
            throw new IllegalStateException("Generation of Java DAML code failed!")
          }
    },
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src-gen",
    Test / publishArtifact := true,
    (Compile / compile) := (Compile / compile).dependsOn(generateJavaCode).value,
    libraryDependencies ++=
      Dependencies.damlToplLib.main ++
      Dependencies.damlToplLib.test
  )

  lazy val damlOnTopl = project
  .in(file("."))
  .settings(
    moduleName := "daml-on-topl",
    commonSettings,
    publish / skip := true
  )
  .aggregate(
    damlToplLib
  )