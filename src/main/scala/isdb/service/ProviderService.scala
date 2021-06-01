package isdb.service

import cats.effect.Async
import cats.MonadThrow
import cats.syntax.all._
import io.janstenpickle.trace4cats.inject.Trace
import isdb.repository.algebra
import io.odin.Logger
import java.util.UUID
import isdb.models.errors._
import isdb.models.providers._
import io.janstenpickle.trace4cats.model.SpanKind

class ProviderService[F[_]: Async: MonadThrow: Trace](repo: algebra.ProviderRepository[F])(implicit L: Logger[F]) {
  def findProvider(id: UUID): F[Option[Provider]] = Trace[F].span("provider-find", SpanKind.Server) {
      repo.find(id)
  }

  def findAllProviders(): F[List[Provider]] = Trace[F].span("provider-find-all", SpanKind.Server) {
      repo.findAll
  }

  def insertProvider(req: CreateProviderRequest): F[Provider] = Trace[F].span("provider-insert", SpanKind.Server) {
      repo.createProvider(req) flatMap {
          case Left(err) => Async[F].raiseError(CreateError(err))
          case Right(provider) => Async[F].delay(provider)
      }
  }
}
