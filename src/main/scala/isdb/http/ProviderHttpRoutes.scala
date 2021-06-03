package isdb.http

import cats.data.{Validated, ValidatedNel, NonEmptyList}
import cats.MonadThrow
import cats.effect.Async
import cats.syntax.all._
import io.circe._
import io.circe.generic.auto._
import io.circe.refined._
import io.circe.syntax._
import io.janstenpickle.trace4cats.inject.Trace
import io.odin.Logger
import isdb.service.ProviderService
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import isdb.models.providers._
import isdb.models.errors._
import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.collection._
import eu.timepit.refined.string._

class ProviderHttpRoutes[F[_]: Async: MonadThrow: Trace](providerService: ProviderService[F])(implicit
    L: Logger[F],
    H: HttpErrorHandler[F]
) extends Http4sDsl[F] {
  import ProviderHttpRoutes._
  implicit def createDecoder[A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "providers" => {
      for {
        providers <- providerService.findAllProviders()
        traceId <- Trace[F].traceId.map(_.getOrElse(""))
        response <- Ok(providers.asJson, Header("X-Trace-Id", traceId))
      } yield response
    }
    case GET -> Root / "providers" / UuidVar(id) => {
      for {
        provider <- providerService
          .findProvider(id)
          .flatMap(_.fold(MonadThrow[F].raiseError[Provider](EntryNotFound(id.toString())))(Async[F].delay(_)))
        traceId <- Trace[F].traceId.map(_.getOrElse(""))
        response <- Ok(provider.asJson)
      } yield response
    }
    case req @ POST -> Root / "providers" => {
      req.decode[CreateProvider] { createReq =>
        for {
          validatedRequest <- toCreateRequest(createReq)
          result <- validatedRequest
            .fold[F[Provider]](errs => Async[F].raiseError(ParseError(errs)), providerService.insertProvider)
          traceId <- Trace[F].traceId.map(_.getOrElse(""))
          response <- Ok(result.asJson, Header("X-Trace-Id", traceId))
        } yield response
      }
    }
  }
}

object ProviderHttpRoutes {
  final case class CreateProvider(name: String, url: String, serviceType: ServiceType)

  def toCreateRequest[F[_]: Async](unvalidated: CreateProvider): F[ValidatedNel[String, CreateProviderRequest]] = {
    val refinements = (
      refineV[NonEmpty](unvalidated.name).toValidatedNel[String],
      refineV[Url](unvalidated.url).toValidatedNel[String],
      Validated.Valid(unvalidated.serviceType)
    ).mapN { (name, url, serviceType) => (name, url, serviceType) }
    Async[F].delay(refinements)
  }
}
