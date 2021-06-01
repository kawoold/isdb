package isdb.repository

import cats.Monad
import cats.effect.Async
import cats.syntax.all._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.util.query.Query0
import doobie.util.transactor.Transactor
import doobie.util.update.Update0
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.SpanKind
import io.odin.Logger
import isdb.models.providers._

import java.util.UUID

class PostgresProviderRepository[F[_]: Async: Monad: Trace](xa: Transactor[F])(implicit L: Logger[F])
    extends algebra.ProviderRepository[F] {
  def createProvider(provider: CreateProviderRequest): F[Either[String, Provider]] =
    Trace[F].span("db-create-provider", SpanKind.Internal) {
      val statement = ProviderStatements.insertProvider(provider)
      val insertAction = for {
        _ <- Trace[F].put("create-req", provider.toTraceable)
        _ <- L.debug(s"Inserting $provider")
        dto <- statement.transact(xa)
        result = dto.toProvider
      } yield result.leftMap(_ => "Unable to create a provider").toEither

      insertAction recoverWith { case UnexpectedEnd =>
        Async[F].delay(Left[String, Provider]("Cannot insert a provider"))
      } flatTap {
        case Left(err)     => Trace[F].put("err", err)
        case Right(result) => Trace[F].put("provider-id", result.id.toString())
      }
    }

  def findAll: F[List[Provider]] = Trace[F].span("db-all-providers", SpanKind.Internal) {
    ProviderStatements.findAll
      .to[List]
      .transact(xa)
      .map(_.flatMap(_.toProvider.toOption)) recoverWith { case UnexpectedEnd =>
      Async[F].delay(Nil)
    } flatTap { providers =>
      L.debug(s"Found ${providers.size} providers")
    }
  }

  def find(id: UUID): F[Option[Provider]] = Trace[F].span("db-find-provider", SpanKind.Internal) {
    ProviderStatements
      .findProvider(id)
      .transact(xa)
      .map(_.flatMap(_.toProvider.toOption)) recoverWith { case UnexpectedEnd =>
      Async[F].delay(None)
    }
  }

}

object ProviderStatements {
  val findAll: Query0[ProviderDTO] = sql"SELECT * FROM providers".query[ProviderDTO]

  def findProvider(id: UUID): ConnectionIO[Option[ProviderDTO]] =
    sql"SELECT * FROM providers WHERE id=${id.toString()}".query[ProviderDTO].option

  def insertProvider(req: CreateProviderRequest): ConnectionIO[ProviderDTO] = {
    val insertStatement = fr"INSERT INTO providers(name, url, service_type)"
    val insertValues = fr"VALUES (${req._1.value}, ${req._2.value}, ${req._3})"

    (insertStatement ++ insertValues).update.withUniqueGeneratedKeys("id", "name", "url", "service_type")
  }
}
