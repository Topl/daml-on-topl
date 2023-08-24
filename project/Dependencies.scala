import sbt._

object Dependencies {

  val catsCoreVersion = "2.10.0"

  lazy val http4sVersion = "0.23.18"

  val damlBindings: Seq[ModuleID] = Seq(
    "com.daml" % "bindings-rxjava" % "2.7.0"
  )

  val logback: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )

  lazy val toplOrg = "co.topl"

  lazy val bramblVersion = "2.0.0-alpha3"

  val bramblSdk = toplOrg %% "brambl-sdk" % bramblVersion

  val bramblCrypto = toplOrg %% "crypto" % bramblVersion

  val bramblServiceKit = toplOrg %% "service-kit" % bramblVersion

  val brambl: Seq[ModuleID] = Seq(bramblSdk, bramblCrypto, bramblServiceKit)

  lazy val munit: Seq[ModuleID] = Seq(
    "org.scalameta" %% "munit" % "1.0.0-M8"
  )

  lazy val munitCatsEffects: Seq[ModuleID] = Seq(
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.6"
  )

  val cats: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core" % catsCoreVersion,
    "org.typelevel" %% "cats-effect" % "3.4.11"
  )

  lazy val scopt: Seq[ModuleID] = Seq("com.github.scopt" %% "scopt" % "4.0.1")

  val fs2Version = "3.5.0"

  lazy val fs2: Seq[ModuleID] = Seq("co.fs2" %% "fs2-core" % fs2Version,
              "co.fs2" %% "fs2-io" % fs2Version,
              "co.fs2" %% "fs2-reactive-streams" % fs2Version)

  lazy val http4s: Seq[ModuleID] = Seq(
            "org.http4s" %% "http4s-ember-client" % http4sVersion,
            "org.http4s" %% "http4s-circe" % http4sVersion)

  object damlToplLib {

    lazy val main: Seq[ModuleID] =
      damlBindings ++
        logback ++
        brambl ++
        cats

    lazy val test: Seq[ModuleID] =
      (
        munit ++ munitCatsEffects
      )
        .map(_ % Test)
  }

  object damlToplBroker {

    lazy val main: Seq[ModuleID] =
        brambl ++
        scopt ++
        cats ++ 
        fs2 ++
        http4s

    lazy val test: Seq[ModuleID] =
      (
        munit ++ munitCatsEffects
      )
        .map(_ % Test)
  }
  // daml-topl-lib
  // daml-on-topl for the name of the repo
}