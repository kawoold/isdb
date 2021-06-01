package isdb.config

import cats.effect.Async
import cats.effect.ContextShift
import cats.effect.Resource
import cats.implicits._
import ciris._
import ciris.refined._
import enumeratum.CirisEnum
import enumeratum.Enum
import enumeratum.EnumEntry
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.cats._
import eu.timepit.refined.collection.MinSize
import eu.timepit.refined.types.net.UserPortNumber
import eu.timepit.refined.types.string.NonEmptyString
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.inject.Trace
import io.odin.Level
import org.http4s.Uri

sealed trait AppEnvironment extends EnumEntry

object AppEnvironment extends Enum[AppEnvironment] with CirisEnum[AppEnvironment] {
  case object Local extends AppEnvironment
  case object Dev extends AppEnvironment

  val values = findValues

  type DatabasePassword = String Refined MinSize[5]
}

import AppEnvironment._

final case class DatabaseConfig(
    username: NonEmptyString,
    password: Secret[DatabasePassword],
    port: UserPortNumber,
    host: NonEmptyString,
    dbName: NonEmptyString
) {
  val connectionUrl: NonEmptyString = NonEmptyString.unsafeFrom(s"jdbc:postgresql://$host:$port/$dbName")
}

final case class GeocodingConfig(baseUrl: Uri, apiKey: Secret[NonEmptyString])

final case class Configuration(
    environment: AppEnvironment,
    logLevel: Level,
    dbConfig: DatabaseConfig,
    geocodingConfig: GeocodingConfig,
    serverPort: UserPortNumber
)

object Configuration {
  private implicit val logLevelDecoder: ConfigDecoder[String, Level] = ConfigDecoder[String].mapOption("Level") { str =>
    str.toLowerCase match {
      case "info"  => Some(Level.Info)
      case "debug" => Some(Level.Debug)
      case "error" => Some(Level.Error)
      case "trace" => Some(Level.Trace)
      case "warn"  => Some(Level.Warn)
      case _       => None
    }
  }

  def configResource[F[_]: Async: ContextShift: Trace]: Resource[F, Configuration] =
    Resource.make(Trace[F].span("config-load")(config[F])) { _ =>
      Trace[F].span("config-teardown") {
        Async[F].delay(())
      }
    }

  def config[F[_]: Async: ContextShift: Trace]: F[Configuration] = Trace[F].span("config-load") {
    env("APP_ENV")
      .or(env("app.env"))
      .or(prop("app.env"))
      .as[AppEnvironment]
      .default(Local)
      .flatMap { appEnv =>
        (
          env("APP_LOGGING").or(env("app.log")).or(prop("app.log")).as[Level].default(Level.Info),
          dbConfig(appEnv),
          geocodingConfig(appEnv),
          env("SERVER_PORT").or(env("port")).or(prop("port")).as[UserPortNumber].default(8000)
        ).parMapN { (logLevel, dbConf, geoConf, port) =>
          Configuration(appEnv, logLevel, dbConf, geoConf, port)
        }
      }
      .load[F]
      .flatTap(cfg => Trace[F].put("cfg-result", cfg.toString()))
  }

  def geocodingConfig(environment: AppEnvironment): ConfigValue[GeocodingConfig] = {
    env("GEO_API_KEY").or(prop("geo.api")).as[NonEmptyString].secret.map { apiKey =>
      GeocodingConfig(
        Uri.unsafeFromString("https://maps.googleapis.com"),
        apiKey
      )
    }
  }

  def dbConfig(environment: AppEnvironment): ConfigValue[DatabaseConfig] = {
    environment match {
      case Local => {
        (
          env("DB_USERNAME").or(prop("db.user")).as[NonEmptyString].default("postgres"),
          env("DB_PASSWORD").or(prop("db.password")).as[DatabasePassword].secret.default(Secret("postgres")),
          env("DB_PORT").or(prop("db.port")).as[UserPortNumber].default(5432),
          env("DB_HOST").or(prop("db.host")).as[NonEmptyString].default("localhost"),
          env("DB_NAME").or(prop("db.name")).as[NonEmptyString].default("postgres")
        ).parMapN(DatabaseConfig)
      }
      case Dev => {
        (
          env("DB_USERNAME").or(prop("db.user")).as[NonEmptyString],
          env("DB_PASSWORD").or(prop("db.password")).as[DatabasePassword].secret,
          env("DB_PORT").or(prop("db.port")).as[UserPortNumber].default(5432),
          env("DB_HOST").or(prop("db.host")).as[NonEmptyString].default("localhost"),
          env("DB_NAME").or(prop("db.name")).as[NonEmptyString].default("users")
        ).parMapN(DatabaseConfig)
      }
    }
  }
}
