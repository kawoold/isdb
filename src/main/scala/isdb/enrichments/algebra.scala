package isdb.enrichments

import fs2.kafka._
import cats.effect.Async
import cats.syntax.all._
import cats.MonadError
import fs2.kafka.ConsumerSettings
import fs2.kafka.Deserializer
import fs2.kafka.AutoOffsetReset
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.Timer
import java.util.UUID
import io.circe.generic.semiauto._
import io.circe.Codec
import io.circe.syntax._
import io.circe.jawn.decodeByteArray
import isdb.config.Configuration
import io.odin.Logger
import scala.concurrent.duration._
import java.nio.charset.Charset

object algebra {
  trait ProviderCheck[F[_]] {
    def listen(): F[Unit]

    def submitRequest(checkRequest: ProviderCheck.ProviderCheckRequest): F[Unit]
  }

  object ProviderCheck {
    final case class ProviderCheckRequest(addressId: UUID)
    implicit val providerCheckCodec: Codec[ProviderCheckRequest] = deriveCodec
  }

  class KafkaProviderCheck[F[_]: Async: ConcurrentEffect](config: Configuration)(implicit
      L: Logger[F],
      CS: ContextShift[F],
      T: Timer[F]
  ) extends ProviderCheck[F] {

    private implicit val reqDeserializer: Deserializer[F, ProviderCheck.ProviderCheckRequest] = Deserializer.lift {
      bytes =>
        decodeByteArray[ProviderCheck.ProviderCheckRequest](bytes) match {
          case Left(err)    => Async[F].raiseError(new RuntimeException(err.getMessage())) // Maaaaaybe handle this better?
          case Right(value) => Async[F].delay(value)
        }
    }

    private implicit val reqSerializer: Serializer[F, ProviderCheck.ProviderCheckRequest] = Serializer.lift { req =>
      Async[F].delay(req.asJson.show.getBytes(Charset.defaultCharset()))
    }
    def listen(): F[Unit] = {
      val consumerSettings = ConsumerSettings(
        keyDeserializer = Deserializer[F, String],
        valueDeserializer = Deserializer[F, ProviderCheck.ProviderCheckRequest]
      ).withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers("localhost:9092")
        .withGroupId("group")

      KafkaConsumer[F].resource(consumerSettings).use { consumer =>
        consumer.subscribeTo("topic") >> consumer.stream
          .mapAsync(25) { committable =>
            val recordToProcess = committable.record.value
            L.info(s"Received processing request for $recordToProcess") *> Async[F].delay(committable.offset)
          }
          .through(commitBatchWithin(10, 30.seconds))
          .compile
          .drain
      }
    }

    def submitRequest(checkRequest: ProviderCheck.ProviderCheckRequest): F[Unit] = {
      val producerSettings = ProducerSettings[F, String, ProviderCheck.ProviderCheckRequest]
        .withBootstrapServers("localhost:9092")
      KafkaProducer
        .stream(producerSettings)
        .flatMap { producer =>
          val record = ProducerRecord("topic", "key", checkRequest)
          fs2.Stream.eval(producer.produce(ProducerRecords.one(record)).flatten)
        }
        .compile
        .drain
    }
  }
}
