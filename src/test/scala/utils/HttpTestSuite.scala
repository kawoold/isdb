package utils

import cats.effect.IO
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.implicits._
import org.http4s.syntax._
import org.http4s.circe._

import scala.util.control.NoStackTrace
import org.scalatest.compatible.Assertion
import isdb.http.HttpErrorHandler
import io.odin.Logger

trait HttpTestSuite extends PureTestSuite with CirceInstances {
  case object DummyError extends NoStackTrace

  implicit def createDecoder[A: Decoder]: EntityDecoder[IO, A] = jsonOf[IO, A]

  private val H: HttpErrorHandler[IO] = new HttpErrorHandler[IO]

  def assertHttp[A: Encoder](routes: HttpRoutes[IO], req: Request[IO])(expectedStatus: Status, expectedBody: A)(implicit
      L: Logger[IO]
  ): IO[Assertion] = {
    L.debug(req.asCurl())
    H.handleRoute(routes).run(req).value.flatMap {
      case Some(resp) =>
        resp.asJson.map { json =>
          assert(resp.status === expectedStatus && json.dropNullValues === expectedBody.asJson.dropNullValues)
        }
      case None => fail("Route Not Found")
    } handleErrorWith { th =>
      fail(s"Unexpected error: ${th.getMessage}")
    }
  }

  def assertResult[A: Encoder: Decoder](
      routes: HttpRoutes[IO],
      req: Request[IO]
  )(expectedStatus: Status, assertions: A => Assertion)(implicit L: Logger[IO]): IO[Assertion] = {
    L.debug(req.asCurl())
    H.handleRoute(routes).run(req).value.flatMap {
      case Some(resp) => {
        assert(resp.status === expectedStatus)
        resp.as[A].map(assertions)
      }
      case None => fail("Route Not Found")
    } handleErrorWith { th =>
      fail(s"Unexpected error: ${th.getMessage}")
    }
  }

  def assertHttpStatus(routes: HttpRoutes[IO], req: Request[IO])(
      expectedStatus: Status
  )(implicit L: Logger[IO]): IO[Assertion] = {
    L.debug(req.asCurl())
    H.handleRoute(routes).run(req).value.map {
      case Some(resp) => assert(resp.status === expectedStatus)
      case None       => fail("Route Not Found")
    } handleErrorWith { th =>
      fail(s"Unexpected error: ${th.getMessage}")
    }
  }

  def assertHttpFailure(routes: HttpRoutes[IO], req: Request[IO])(implicit L: Logger[IO]): IO[Assertion] = {
    L.debug(req.asCurl())
    H.handleRoute(routes).run(req).value.attempt.map {
      case Left(_)  => assert(true)
      case Right(_) => fail("Expected a failure")
    } handleErrorWith { th =>
      fail(s"Unexpected error: ${th.getMessage}")
    }
  }
}
