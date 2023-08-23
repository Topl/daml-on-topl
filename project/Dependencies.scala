import sbt._

object Dependencies {

  val catsCoreVersion = "2.9.0"

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
        .map(_ % "it,test")
  }
  // daml-topl-lib
  // daml-on-topl for the name of the repo
}
