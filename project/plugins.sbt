
Seq(
  "com.eed3si9n"            % "sbt-assembly"              % "2.1.1",
  "org.scalameta"           % "sbt-scalafmt"              % "2.5.0",
  "ch.epfl.scala"           % "sbt-scalafix"              % "0.11.0",
  "com.eed3si9n"            % "sbt-buildinfo"             % "0.11.0",
  "com.github.sbt"          % "sbt-native-packager"       % "1.9.9",
  "com.github.sbt"          % "sbt-ci-release"            % "1.5.12",
).map(addSbtPlugin)