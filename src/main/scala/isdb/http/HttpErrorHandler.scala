package isdb.http

import cats.Monad
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import isdb.models.errors._
import org.http4s.Response
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import cats.Show
import org.http4s.server.ServiceErrorHandler
import org.http4s.Request
import org.http4s.HttpRoutes
import cats.data.Kleisli
import java.net.http.HttpRequest
import cats.MonadThrow
import cats.MonadError
import cats.data.OptionT
import cats.effect.Async
import org.http4s.HttpVersion
import org.http4s.MessageFailure

class HttpErrorHandler[F[_]: Async] extends Http4sDsl[F] {
  val handle: Throwable => F[Response[F]] = { case f: MessageFailure =>
    Async[F].delay(f.toHttpResponse(HttpVersion.`HTTP/2.0`))
  }

  def handleRoute(routes: HttpRoutes[F]): HttpRoutes[F] = Kleisli { req: Request[F] =>
    OptionT(routes.run(req).value.handleErrorWith(e => handle(e).map(Option(_))))
  }
}
