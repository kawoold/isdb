package isdb

import cats.effect._
import cats.syntax.all._
import doobie.util.transactor.Transactor
import io.janstenpickle.trace4cats.inject.Trace
import io.odin.Level
import io.odin._
import io.circe.parser._
import isdb.config.Configuration
import isdb.http.AddressHttpRoutes
import isdb.http.HttpErrorHandler
import isdb.http.ProviderHttpRoutes
import isdb.repository.PostgresAddressRepository
import isdb.repository.PostgresProviderRepository
import isdb.repository.algebra
import isdb.service.AddressService
import isdb.service.ProviderService
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import scala.concurrent.ExecutionContext
import cats.Monad
import isdb.service.GeocodingFinder
import isdb.service.GoogleGeocodingFinder
import dev.profunktor.auth.JwtAuthMiddleware
import isdb.models.users._
import dev.profunktor.auth.jwt._
import org.http4s.server.AuthMiddleware
import io.janstenpickle.trace4cats.model.SpanKind
import isdb.service.Security

class Module[F[_]: Concurrent: ContextShift: Timer: Trace](config: Configuration, client: Client[F]) {

  private implicit val clock: Clock[F] = Clock.create

  private implicit val Logger: Logger[F] = consoleLogger[F](
    formatter = formatter.Formatter.colorful,
    minLevel = config.logLevel
  )

  private val xa: Transactor[F] = Transactor.fromDriverManager[F](
    "org.postgresql.Driver",
    config.dbConfig.connectionUrl.value,
    config.dbConfig.username.value,
    config.dbConfig.password.value.value
  )

  private implicit val S: Security[F] = Security.mkSecurity[F]

  private val addressRepo: algebra.AddressRepository[F] = new PostgresAddressRepository[F](xa)

  private val geo: GeocodingFinder[F] = new GoogleGeocodingFinder[F](config, client)

  private val addressService: AddressService[F] = new AddressService[F](addressRepo, geo)

  private val providerRepo: algebra.ProviderRepository[F] = new PostgresProviderRepository[F](xa)

  private val providerService: ProviderService[F] = new ProviderService[F](providerRepo)

  private implicit val errorHandler: HttpErrorHandler[F] = new HttpErrorHandler[F]

  private val addressRoutes: HttpRoutes[F] = new AddressHttpRoutes[F](addressService).routes

  private val providerRoutes: HttpRoutes[F] = new ProviderHttpRoutes[F](providerService).routes

  val httpApp: HttpApp[F] = errorHandler.handleRoute(addressRoutes <+> providerRoutes).orNotFound
}
