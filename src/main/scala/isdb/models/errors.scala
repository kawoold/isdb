package isdb.models

import cats.data.NonEmptyList
import cats.Show
import org.http4s.MessageFailure
import org.http4s.{HttpVersion, Response}
import org.http4s.Status
import org.http4s.circe._
import io.circe.Json
import io.circe.JsonObject
import org.http4s.EntityEncoder

object errors {
  sealed trait ApiError extends RuntimeException with Product with Serializable
  final case class CreateError(error: String) extends ApiError with MessageFailure {

    def cause: Option[Throwable] = Some(this)

    def message: String = error

    def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
      Response(Status.BadRequest, httpVersion)
        .withEntity(message)
  }
  final case class EntryNotFound(key: String) extends ApiError with MessageFailure {
    def cause: Option[Throwable] = Some(this)

    def message: String = s"No entry found for $key"

    def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
      Response(Status.NotFound, httpVersion)
        .withEntity(message)
  }
  final case class ParseError(errors: NonEmptyList[String]) extends ApiError with MessageFailure {
    def cause: Option[Throwable] = Some(this)

    def message: String = s"Could not parse data: ${Show[NonEmptyList[String]].show(errors)}"

    def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
      Response(Status.UnprocessableEntity, httpVersion)
        .withEntity(
          Json.obj(
            "errors" -> Json.arr(errors.map(Json.fromString).toList: _*),
            "message" -> Json.fromString("Could not parse JSON")
          )
        )
  }
}
