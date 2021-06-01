package isdb.repository

import cats.Monad
import cats.effect.Async
import cats.syntax.all._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.circe.json.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.util.query.Query0
import doobie.util.transactor.Transactor
import doobie.util.update.Update0
import doobie.postgres.implicits._
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.SpanKind
import io.odin.Logger
import isdb.models._
import isdb.models.address._

import java.util.UUID

import algebra.AddressRepository
import io.circe.Json
import doobie.util.Get
import doobie.util.Put

class PostgresAddressRepository[F[_]: Async: Monad: Trace](xa: Transactor[F])(implicit L: Logger[F])
    extends AddressRepository[F] {
  def createAddress(addr: CreateAddressRequest): F[Either[String, Address]] =
    Trace[F].span("db-create-address", SpanKind.Internal) {
      val statement = AddressStatements.insertAddress(addr)

      statement
        .map(_.toAddress.toRight("Cannot Insert Address"))
        .transact(xa)
        .recoverWith { case UnexpectedEnd =>
          Async[F].delay(Left[String, Address]("Cannot Insert Address"))
        } flatTap {
        case Left(err)   => Trace[F].put("err", err)
        case Right(addr) => Trace[F].put("address-id", addr.id.toString())
      }
    }

  def findAll: F[List[Address]] = Trace[F].span("db-all-addresses", SpanKind.Internal) {
    val statement: ConnectionIO[List[AddressDTO]] = AddressStatements.findAll.to[List]

    val program = for {
      rawAddresses <- statement.transact(xa)
      _ <- L.debug(s"Got ${rawAddresses.size} raw addresses")
      _ <- L.debug(s"Raw addresses = $rawAddresses")

    } yield rawAddresses.flatMap(_.toAddress)

    program.recoverWith {
      case UnexpectedEnd => L.error(s"Ran into an unexpected End") *> Async[F].delay(Nil)
      case err           => L.error(s"Failed to get all addresses: ${err.getMessage}") *> Async[F].delay(Nil)
    }
  }

  def find(id: UUID): F[Option[Address]] = Trace[F].span("db-find-address", SpanKind.Internal) {
    val findAction = AddressStatements
      .findAddress(id)
      .map(_.flatMap(_.toAddress))
      .transact(xa)
      .recoverWith { case UnexpectedEnd =>
        Async[F].delay(None)
      }

    for {
      _ <- Trace[F].put("id", id.toString())
      result <- findAction
    } yield result
  }

}

private object AddressStatements {
  private val selectAddress = fr"SELECT * FROM addresses"
  val findAll: Query0[AddressDTO] = sql"$selectAddress".query[AddressDTO]

  def findAddress(id: UUID): ConnectionIO[Option[AddressDTO]] =
    sql"$selectAddress WHERE id=${id.toString()}".query[AddressDTO].option

  def insertAddress(addr: CreateAddressRequest): ConnectionIO[AddressDTO] = {
    val statement = sql"""
    |INSERT INTO addresses (line_1, line_2, state_code, zip_code, geo_results)
    |VALUES
    | (${addr._1.value}, ${addr._2.map(_.value)}, ${addr._3.value}, ${addr._4.value}, ${addr._5})
    |""".stripMargin
    statement.update.withUniqueGeneratedKeys("id", "line_1", "line_2", "state_code", "zip_code", "geo_results")
  }

}
