package isdb.service

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import dev.profunktor.auth.jwt._
import org.http4s.server.AuthMiddleware
import isdb.models.users._
import dev.profunktor.auth.JwtAuthMiddleware
import io.circe.parser._
import cats.effect.Async
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.SpanKind

trait Security[F[_]] {
  def middlewareWithRole(requiredRole: String Refined NonEmpty): AuthMiddleware[F, UserData]

  def authenticatedMiddleware: AuthMiddleware[F, UserData]
}

object Security {
  def mkSecurity[F[_]: Async: Trace]: Security[F] = new Security[F] {
    def middlewareWithRole(requiredRole: String Refined NonEmpty): AuthMiddleware[F, UserData] =
      JwtAuthMiddleware[F, UserData](
        JwtAuth.noValidation,
        tkn =>
          claim =>
            Trace[F].span(s"auth-check-$requiredRole", SpanKind.Internal) {
              Async[F].delay(
                parse(claim.content).flatMap(_.as[UserData]).toOption.filter(u => u.hasRole(requiredRole.value))
              )
            }
      )

    def authenticatedMiddleware: AuthMiddleware[F, UserData] = JwtAuthMiddleware[F, UserData](
      JwtAuth.noValidation,
      tkn =>
        claim =>
          Trace[F].span("auth-check", SpanKind.Internal) {
            Async[F].delay(
              parse(claim.content).flatMap(_.as[UserData]).toOption
            )
          }
    )
  }
}
