package isdb
import cats.Monad
import cats.data.Kleisli
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.client.syntax._
import io.janstenpickle.trace4cats.http4s.server.syntax._
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.jaeger.JaegerSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.TraceProcess
import isdb.config.Configuration
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext
import isdb.http.HttpErrorHandler
import org.http4s.client.blaze.BlazeClientBuilder
import cats.data

object Server extends IOApp {

  def entryPoint[F[_]: Concurrent: ContextShift: Timer](
      blocker: Blocker,
      process: TraceProcess
  ): Resource[F, EntryPoint[F]] = {
    JaegerSpanCompleter.apply[F](blocker, process, "localhost", 6831).map { completer =>
      EntryPoint[F](SpanSampler.always[F], completer)
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    val serverResource: Resource[IO, fs2.Stream[IO, ExitCode]] = for {
      blocker <- Blocker[IO]
      ep <- entryPoint[IO](blocker, TraceProcess("isdb"))
      appServer = new HttpServer[IO]
      server <- appServer.server(ep)
      s <- Resource.eval(server)
    } yield s

    serverResource.use(_.compile.drain.map(_ => ExitCode.Success))
  }
}

class HttpServer[F[_]: ContextShift: ConcurrentEffect: Timer: Monad] {

  def server(ep: EntryPoint[F]): Resource[F, F[fs2.Stream[F, ExitCode]]] = {
    val ec: ExecutionContext = ExecutionContext.global
    for {
      entry <- ep.root("isdb-service")
      client <- BlazeClientBuilder[F](ec).resource
    } yield {
      Configuration.config[Kleisli[F, Span[F], *]].run(entry).map(cfg => {
        val module = new Module[Kleisli[F, Span[F], *]](cfg, client.liftTrace())
        val serverBuilder = BlazeServerBuilder[F](ec).bindHttp(cfg.serverPort.value, "0.0.0.0").withHttpApp(module.httpApp.inject(ep))
        serverBuilder.serve
      })
    }
  }
}
