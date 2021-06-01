
lazy val CatsEffectVersion    = "2.3.1"
lazy val Fs2Version           = "2.5.0"
lazy val Http4sVersion        = "0.21.20"
lazy val CirceVersion         = "0.12.3"
lazy val DoobieVersion        = "0.12.1"
lazy val H2Version            = "1.4.196"
lazy val FlywayVersion        = "5.0.5"
lazy val LogbackVersion       = "1.2.3"
lazy val ScalaTestVersion     = "3.2.5"
lazy val ScalaTestPlus        = "3.2.2.0"
lazy val ScalaCheckVersion    = "1.14.1"
lazy val OdinVersion          = "0.11.0"
lazy val CirisVersion         = "1.2.1"
lazy val RefinedVersion       = "0.9.23"
lazy val Trace4CatsVersion    = "0.10.1"
lazy val KindProjectorVersion = "0.11.3"
lazy val EnumeratumVersion    = "1.6.1"
lazy val MonocleVersion       = "3.0.0-M5"
lazy val Http4sJwtVersion     = "0.0.6"

wartremoverErrors ++= Warts.all

addCompilerPlugin("org.typelevel" % "kind-projector" % KindProjectorVersion cross CrossVersion.full)

lazy val root = (project in file("."))
  .settings(
    organization := "isdb",
    name := "internet-service-database",
    version := "1.0.0-SNAPSHOT",
    scalaVersion := "2.13.5",
    scalacOptions := Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel"   %% "cats-effect"         % CatsEffectVersion,
      "co.fs2"          %% "fs2-core"            % Fs2Version,

      "org.http4s"                  %% "http4s-blaze-server"  % Http4sVersion,
      "org.http4s"                  %% "http4s-blaze-client"  % Http4sVersion,
      "org.http4s"                  %% "http4s-circe"         % Http4sVersion,
      "org.http4s"                  %% "http4s-dsl"           % Http4sVersion,
      "dev.profunktor"              %% "http4s-jwt-auth"      % Http4sJwtVersion,
      "io.circe"                    %% "circe-core"           % CirceVersion,
      "io.circe"                    %% "circe-refined"        % CirceVersion,
      "io.circe"                    %% "circe-generic"        % CirceVersion,
      "io.circe"                    %% "circe-derivation"     % "0.13.0-M5",
      "com.github.julien-truffaut"  %% "monocle-core"         % MonocleVersion,
      "com.github.julien-truffaut"  %% "monocle-macro"        % MonocleVersion,

      "com.h2database"  %  "h2"                  % H2Version,
      "org.flywaydb"    %  "flyway-core"         % FlywayVersion,
      "org.tpolecat"    %% "doobie-core"         % DoobieVersion,
      "org.tpolecat"    %% "doobie-postgres"     % DoobieVersion,
      "org.tpolecat"    %% "doobie-postgres-circe" % DoobieVersion,
      "org.tpolecat"    %% "doobie-h2"           % DoobieVersion,

      "com.github.valskalla" %% "odin-core"      % OdinVersion,
      "com.github.valskalla" %% "odin-json"      % OdinVersion,
      "com.github.valskalla" %% "odin-extras"    % OdinVersion,

      "is.cir"          %% "ciris"               % CirisVersion,
      "is.cir"          %% "ciris-enumeratum"    % CirisVersion,
      "is.cir"          %% "ciris-refined"       % CirisVersion,
      "eu.timepit"      %% "refined-cats"        % RefinedVersion,

      "io.janstenpickle"  %% "trace4cats-core"                    % Trace4CatsVersion,
      "io.janstenpickle"  %% "trace4cats-inject"                  % Trace4CatsVersion,
      "io.janstenpickle"  %% "trace4cats-avro-exporter"           % Trace4CatsVersion,
      "io.janstenpickle"  %% "trace4cats-jaeger-thrift-exporter"  % Trace4CatsVersion,
      "io.janstenpickle"  %% "trace4cats-http4s-server"           % Trace4CatsVersion,
      "io.janstenpickle"  %% "trace4cats-http4s-client"           % Trace4CatsVersion,
      "com.beachape"      %% "enumeratum"                         % EnumeratumVersion,
      "com.beachape"      %% "enumeratum-circe"                   % EnumeratumVersion,

      "org.scalatest"     %% "scalatest"           % ScalaTestVersion  % Test,
      "org.scalatestplus" %% "scalacheck-1-14"     % ScalaTestPlus,
      "org.scalacheck"    %% "scalacheck"          % ScalaCheckVersion % Test,
      "org.tpolecat"      %% "doobie-scalatest"    % DoobieVersion % Test,
      "eu.timepit"        %% "refined-scalacheck"  % RefinedVersion
    )
  )

