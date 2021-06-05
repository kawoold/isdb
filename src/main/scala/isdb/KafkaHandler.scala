package isdb

import cats.effect.IOApp
import io.janstenpickle.trace4cats.fs2.syntax._
import isdb.enrichments.algebra
import cats.effect.IO
import cats.effect.Async
import io.janstenpickle.trace4cats.model.TraceProcess
import cats.effect.Resource
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.jaeger.JaegerSpanCompleter
import cats.effect.Blocker
import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.Timer
import io.janstenpickle.trace4cats.kernel.SpanSampler
import cats.effect.ExitCode
import cats.effect.ConcurrentEffect
import scala.concurrent.ExecutionContext

object KafkaHandler extends IOApp {
  def entryPoint[F[_]: Concurrent: ContextShift: Timer](
      blocker: Blocker,
      process: TraceProcess
  ): Resource[F, EntryPoint[F]] = {
    JaegerSpanCompleter.apply[F](blocker, process, "localhost", 6831).map { completer =>
      EntryPoint[F](SpanSampler.always[F], completer)
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    val runner = for {
      blocker <- Blocker[IO]
      ep <- entryPoint[IO](blocker, TraceProcess("isdb-kafka"))
      runner = new KafkaRunner[IO](executionContext)
    } yield ???
    ???
  }
}

class KafkaRunner[F[_]: ConcurrentEffect](ec: ExecutionContext) {}
