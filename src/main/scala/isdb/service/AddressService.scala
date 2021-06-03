package isdb.service

import cats.MonadThrow
import cats.syntax.all._
import io.janstenpickle.trace4cats.inject.Trace
import isdb.repository.algebra._
import cats.effect.Async
import io.odin.Logger
import java.util.UUID
import isdb.models.address._
import isdb.models.errors._
import io.janstenpickle.trace4cats.model.SpanKind
import cats.Show

class AddressService[F[_]: Async: MonadThrow: Trace](addressRepo: AddressRepository[F], geo: GeocodingFinder[F])(
    implicit L: Logger[F]
) {

  def findAddress(id: UUID): F[Option[Address]] = Trace[F].span("address-find", SpanKind.Server) {
    for {
      _ <- Trace[F].put("id", id.toString())
      result <- addressRepo.find(id)
      _ <- L.debug(s"Address lookup for $id = $result")
    } yield result
  }

  def insertAddress(address: CreateAddressRequest): F[Address] = Trace[F].span("address-insert", SpanKind.Server) {
    for {
      _ <- Trace[F].put("request", address.toTraceable)
      geoResult <- geo.lookupGeocode(address.formattedAddress)
      resultAttempt <- addressRepo.createAddress(address.copy(_5 = Option(geoResult)))
      _ <- L.debug(s"Address Creation Result: $resultAttempt")
      result <- resultAttempt match {
        case Left(err) => Async[F].raiseError(CreateError(err))
        case Right(r)  => Async[F].delay(r)
      }
      _ <- L.debug(geoResult)
    } yield result
  }

  def findAll: F[List[Address]] = Trace[F].span("address-find-all", SpanKind.Server) {
    addressRepo.findAll
      .flatTap { addrs =>
        Trace[F].put("addr-count", addrs.size)
      }
  }
}
