package isdb
import cats.Monad
import cats.data.Kleisli
import cats.effect._
import cats.syntax.all._
import fs2.kafka._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.http4s.client.syntax._
import io.janstenpickle.trace4cats.http4s.server.syntax._
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.jaeger.JaegerSpanCompleter
import io.janstenpickle.trace4cats.kafka.syntax._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.TraceProcess
import isdb.config.Configuration
import org.http4s.CharsetRange.*
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

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
      appServer = new HttpServer[IO](executionContext)
      server <- appServer.server(ep)
      s <- Resource.eval(server)
    } yield s

    serverResource.use(_.compile.drain.map(_ => ExitCode.Success))
  }
}

class HttpServer[F[_]: ContextShift: ConcurrentEffect: Timer: Monad](ec: ExecutionContext) {

  def server(ep: EntryPoint[F]): Resource[F, F[fs2.Stream[F, ExitCode]]] = {
    for {
      entry <- ep.root("isdb-service")
      client <- BlazeClientBuilder[F](ec).resource
    } yield {
      Configuration
        .config[Kleisli[F, Span[F], *]]
        .run(entry)
        .map(cfg => {
          val consumerSettings = ConsumerSettings(Deserializer.string[F], Deserializer.string[F])
            .withBootstrapServers("localhost:9092")
            .withGroupId("isdb-consumer")

          val consumerStream: TracedStream[
            Kleisli[F, Span[F], *],
            CommittableConsumerRecord[Kleisli[F, Span[F], *], String, String]
          ] = KafkaConsumer[F].stream(consumerSettings).flatMap(_.stream).injectK[Kleisli[F, Span[F], *]](ep)

          val module = new Module[Kleisli[F, Span[F], *]](cfg, client.liftTrace(), consumerStream)
          val serverBuilder =
            BlazeServerBuilder[F](ec).bindHttp(cfg.serverPort.value, "0.0.0.0").withHttpApp(module.httpApp.inject(ep))
          serverBuilder.serve
        })
    }
  }
}
