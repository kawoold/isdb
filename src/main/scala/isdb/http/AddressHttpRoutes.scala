package isdb.http

import cats.MonadThrow
import cats.effect._
import cats.syntax.all._
import cats.instances._
import cats.data.{ValidatedNel, Validated}
import eu.timepit.refined._
import eu.timepit.refined.api.{Refined, RefType}
import eu.timepit.refined.collection._
import eu.timepit.refined.auto._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.refined._
import io.circe.syntax._
import io.janstenpickle.trace4cats.inject.Trace
import io.odin.Logger
import isdb.models.address._
import isdb.models.errors._
import isdb.service.AddressService
import org.http4s.HttpRoutes
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import AddressHttpRoutes._
import org.http4s.server.AuthMiddleware
import isdb.models.users._
import org.http4s.server.Router
import isdb.service.Security

class AddressHttpRoutes[F[_]: Async: MonadThrow: Trace](addressService: AddressService[F])(implicit
    H: HttpErrorHandler[F],
    L: Logger[F],
    S: Security[F]
) extends Http4sDsl[F] {
  implicit def createDecoder[A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

  private val viewAddressMiddleware: AuthMiddleware[F, UserData] = S.middlewareWithRole("view-addresses")

  private val submitAddressMiddleware: AuthMiddleware[F, UserData] = S.middlewareWithRole("submit-addresses")

  private val viewRoutes: AuthedRoutes[UserData, F] = AuthedRoutes.of {
    case GET -> Root as user => {
      for {
        addresses <- addressService.findAll
        traceId <- Trace[F].traceId.map(_.getOrElse(""))
        response <- Ok(addresses.asJson, Header("X-Trace-Id", traceId))
      } yield response
    }
    case GET -> Root / UuidVar(id) as user => {
      for {
        address <- addressService
          .findAddress(id)
          .flatMap(_.fold(MonadThrow[F].raiseError[Address](EntryNotFound(id.toString())))(Async[F].delay(_)))
        traceId <- Trace[F].traceId.map(_.getOrElse(""))
        response <- Ok(address.asJson, Header("X-Trace-Id", traceId))
      } yield response
    }
  }

  private val submitRoutes: AuthedRoutes[UserData, F] = AuthedRoutes.of {
    case ar @ POST -> Root as user => {
      ar.req.decode[CreateAddress] { createAddress =>
        for {
          _ <- L.info(s"Received request to add $createAddress")
          validatedRequest <- toCreateRequest[F](createAddress)
          result <- validatedRequest
            .fold[F[Address]](errs => Async[F].raiseError(ParseError(errs)), addressService.insertAddress)
          traceId <- Trace[F].traceId.map(_.getOrElse(""))
          response <- Ok(result.asJson, Header("X-Trace-Id", traceId))
        } yield response
      }
    }
  }

  val routes: HttpRoutes[F] = Router(
    "/address" -> (viewAddressMiddleware(viewRoutes) <+> submitAddressMiddleware(submitRoutes))
  )
}

object AddressHttpRoutes {
  final case class CreateAddress(
      line1: String,
      line2: Option[String],
      state: String,
      zipcode: String
  )

  def toCreateRequest[F[_]: Sync: Trace](
      unvalidatedRequest: CreateAddress
  ): F[ValidatedNel[String, CreateAddressRequest]] = Trace[F].span("create-validation") {
    val refinements = (
      refineV[NonEmpty](unvalidatedRequest.line1).toValidatedNel,
      unvalidatedRequest.line2.map(refineV[NonEmpty](_)).sequence.toValidatedNel,
      RefType.applyRef[State](unvalidatedRequest.state).toValidatedNel,
      RefType.applyRef[ZipCode](unvalidatedRequest.zipcode).toValidatedNel
    ).mapN { (l1, l2, s, z) =>
      (l1, l2, s, z, None)
    }
    Sync[F].delay(refinements)
  }
}
