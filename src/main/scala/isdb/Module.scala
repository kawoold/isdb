package isdb

import cats.effect._
import cats.syntax.all._
import doobie.util.transactor.Transactor
import fs2.kafka.CommittableConsumerRecord
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.inject.Trace
import io.odin._
import isdb.config.Configuration
import isdb.http.{AddressHttpRoutes, HttpErrorHandler, ProviderHttpRoutes}
import isdb.repository.{PostgresAddressRepository, PostgresProviderRepository, algebra}
import isdb.service._
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.client.Client
import org.http4s.implicits._

class Module[F[_]: Concurrent: ContextShift: Timer: Trace](
    config: Configuration,
    client: Client[F],
    consumerStream: TracedStream[F, CommittableConsumerRecord[F, String, String]]
) {

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
