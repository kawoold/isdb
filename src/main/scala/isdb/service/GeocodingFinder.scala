package isdb.service

import cats.MonadThrow
import cats.effect._
import cats.syntax.all._
import ciris.Secret
import eu.timepit.refined.types.string
import io.circe._
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.SpanKind
import isdb.config.Configuration
import isdb.models.address._
import monocle.Getter
import monocle.macros.GenLens
import org.http4s._
import org.http4s.circe._
import org.http4s.client._
import io.odin.Logger

trait GeocodingFinder[F[_]] {
  def lookupGeocode(address: String): F[GeocodingResults]
}

class GoogleGeocodingFinder[F[_]: Async: Trace](
    config: Configuration,
    client: Client[F]
)(implicit L: Logger[F])
    extends GeocodingFinder[F] {

  implicit def createDecoder[A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

  implicit val gr: EntityDecoder[F, GeocodingResults] = jsonOf[F, GeocodingResults]

  private val baseUrlLens = GenLens[Configuration](_.geocodingConfig.baseUrl)
  private val apiKeyLens = Getter[Configuration, String](_.geocodingConfig.apiKey.value.value)

  def lookupGeocode(address: String): F[GeocodingResults] = Trace[F].span("geocode-lookup", SpanKind.Client) {
    val target = (baseUrlLens.get(config) / "maps" / "api" / "geocode" / "json")
      .withQueryParam("address", address)
      .withQueryParam("key", apiKeyLens.get(config))
    client
      .expect[GeocodingResults](target)
      .recoverWith({ case th =>
        L.error(s"Failed to get geocoding results for $address: ${th.getMessage}", th) *> Async[F].raiseError(th)
      })
  }
}
