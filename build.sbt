
inThisBuild(
  List(
    organization := "co.topl",
    homepage := Some(url("https://github.com/Topl/daml-on-topl")),
    licenses := Seq("MPL2.0" -> url("https://www.mozilla.org/en-US/MPL/2.0/")),
    scalaVersion := "2.13.11"
  )
)

lazy val cleanSrcGenLib = taskKey[Unit]("Clean DAML Generated Code for lib")

lazy val cleanDarLib = taskKey[Unit]("Clean DAML DAR for lib")

lazy val generateJavaCode = taskKey[Unit]("Build DAML package and generate code")

lazy val compileDaml = taskKey[Unit]("Compile DAML")

lazy val damlSourceLib = taskKey[Seq[File]]("DAML Source Code Location for lib")

lazy val damlSourceTrigger = taskKey[Seq[File]]("DAML Source Code Location for trigger")


lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-language:higherKinds",
  "-language:postfixOps",
  "-Ywarn-unused",
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
  homepage := Some(url("https://github.com/Topl/daml-on-topl")),
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

lazy val commonDockerSettings = List(
  Docker / version := dynverGitDescribeOutput.value.mkVersion(versionFmt, fallbackVersion(dynverCurrentDate.value)),
  dockerAliases := dockerAliases.value.flatMap { alias =>
    Seq(
      alias.withRegistryHost(Some("docker.io/toplprotocol")),
      alias.withRegistryHost(Some("ghcr.io/topl"))
    )
  },
  dockerBaseImage := "adoptopenjdk/openjdk11:jdk-11.0.16.1_1-ubuntu",
  dockerUpdateLatest := true
)

lazy val dockerPublishSettingsBroker = List(
  dockerExposedPorts ++= Seq(9000, 9001),
  Docker / packageName := "daml-topl-broker"
) ++ commonDockerSettings

lazy val dockerPublishSettingsDapp = List(
  dockerExposedPorts ++= Seq(9000, 9001),
  Docker / version := dynverGitDescribeOutput.value.mkVersion(versionFmt, fallbackVersion(dynverCurrentDate.value)),
  Docker / packageName := "daml-topl-dapp"
) ++ commonDockerSettings

def versionFmt(out: sbtdynver.GitDescribeOutput): String = {
  val dirtySuffix = out.dirtySuffix.dropPlus.mkString("-", "")
  if (out.isCleanAfterTag) out.ref.dropPrefix + dirtySuffix // no commit info if clean after tag
  else out.ref.dropPrefix + out.commitSuffix.mkString("-", "-", "") + dirtySuffix
}


def fallbackVersion(d: java.util.Date): String = s"HEAD-${sbtdynver.DynVer timestamp d}"

lazy val mavenPublishSettings = List(
  organization := "co.topl",
  homepage := Some(url("https://github.com/Topl/daml-on-topl")),
  licenses := List("MPL2.0" -> url("https://www.mozilla.org/en-US/MPL/2.0/")),
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  developers := List(
    Developer(
      "mundacho",
      "Edmundo Lopez Bobeda",
      "e.lopez@topl.me",
      url("https://github.com/mundacho")
    ),
    Developer(
      "scasplte2",
      "James Aman",
      "j.aman@topl.me",
      url("https://github.com/scasplte2")
    )
    )
)



lazy val damlToplLib = project
  .in(file("daml-topl-lib"))
  .settings(
    name := "daml-topl-lib",
    commonSettings,
    publishSettings,
    damlSourceLib := {
         sbt.nio.file.FileTreeView.default
        .list(Seq(Glob(baseDirectory.value) / "daml.yaml",Glob(baseDirectory.value) / "daml" / ** / "*.daml" ))
        .map(_._1.toFile)
    },
    cleanSrcGenLib := {
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
    cleanDarLib := {
          import scala.sys.process._
          val s: TaskStreams = streams.value
          val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
          val cleanSrc: Seq[String] = shell :+ ("rm -rf " + baseDirectory.value + "/.daml/")
          if((cleanSrc !) == 0) {
            s.log.success("Successfully cleaned .daml for lib!")
          } else {
            throw new IllegalStateException(".daml clean up failed for lib!")
          }
    },
    generateJavaCode := {
          import scala.sys.process._
          val s: TaskStreams = streams.value
          val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
          val buildDamlPackage: Seq[String] = shell :+ ("cd " + baseDirectory.value + " && daml build && daml codegen java")
          val in = damlSourceLib.value.toSet
          val cachedFun = FileFunction.cached(s.cacheDirectory / "damlGen") { (in: Set[File]) =>
            s.log.info("Generating Java DAML code...")
            if ((buildDamlPackage !) == 0) {
              s.log.info("Generation of Java code successful.")
              Set()
            } else {
              s.log.error("Generation of Java DAML code failed")
              in
            }
          }
          if(cachedFun(in).size == 0) {
            ()
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


lazy val damlToplBrokerTrigger = project
  .in(file("daml-topl-broker-trigger"))
  .settings(
    name := "daml-topl-broker-trigger",
    commonSettings,
    publishSettings,
    damlSourceTrigger := {
         sbt.nio.file.FileTreeView.default
        .list(Seq(Glob(baseDirectory.value) / "daml.yaml",Glob(baseDirectory.value) / "daml" / ** / "*.daml" ))
        .map(_._1.toFile)
    },
    compileDaml := ({
          import scala.sys.process._
          val s: TaskStreams = streams.value
          val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
          val buildDamlPackage: Seq[String] = shell :+ ("cd " + baseDirectory.value + " && daml build")
          val in = damlSourceTrigger.value.toSet
          val cachedFun = FileFunction.cached(s.cacheDirectory / "damlGen") { (in: Set[File]) =>
            s.log.info("Build DAML trigger...")
            if ((buildDamlPackage !) == 0) {
              s.log.info("DAML trigger built.")
              Set()
            } else {
              s.log.error("DAML trigger build failed")
              in
            }
          }
          if(cachedFun(in).size == 0) {
            ()
          } else {
            throw new IllegalStateException("DAML trigger build failed!")
          }
    }),
    compileDaml := compileDaml.dependsOn(damlToplLib/Compile/compile).value,
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src-gen",
    Test / publishArtifact := true,
    (Compile / compile) := (Compile / compile).dependsOn(compileDaml).value,
    libraryDependencies ++=
      Dependencies.damlToplLib.main ++
      Dependencies.damlToplLib.test
  )
  .dependsOn(damlToplLib)

lazy val damlToplBroker = (project in file("daml-topl-broker"))
.settings(if (sys.env.get("DOCKER_PUBLISH").getOrElse("false").toBoolean) dockerPublishSettingsBroker else mavenPublishSettings)
.settings(
  commonSettings,
  name := "daml-topl-broker",
  libraryDependencies ++=
    Dependencies.damlToplBroker.main ++
    Dependencies.damlToplBroker.test
).enablePlugins(DockerPlugin, JavaAppPackaging)
.dependsOn(damlToplLib, damlToplShared)

lazy val damlToplApp = (project in file("daml-topl-dapp"))
.settings(if (sys.env.get("DOCKER_PUBLISH").getOrElse("false").toBoolean) dockerPublishSettingsDapp else mavenPublishSettings)
.settings(
  commonSettings,
  name := "daml-topl-dapp",
  libraryDependencies ++= 
    Dependencies.damlToplBroker.main ++
    Dependencies.damlToplBroker.test
).enablePlugins(DockerPlugin, JavaAppPackaging)
.dependsOn(damlToplLib, damlToplShared)

lazy val damlToplShared = (project in file("daml-topl-shared"))
.settings(mavenPublishSettings)
.settings(
  commonSettings,
  name := "daml-topl-shared",
  libraryDependencies ++= 
    Dependencies.damlToplBroker.main ++
    Dependencies.damlToplBroker.test
).enablePlugins(JavaAppPackaging)
.dependsOn(damlToplLib)


lazy val damlOnTopl = project
.in(file("."))
.settings(
  moduleName := "daml-on-topl",
  commonSettings,
  publish / skip := true
)
.aggregate(
  damlToplLib,
  damlToplBroker,
  damlToplApp,
  damlToplBrokerTrigger
)