package isdb

import cats.effect._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import io.janstenpickle.trace4cats.ErrorHandler
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.AttributeValue
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.model.SpanStatus
import io.janstenpickle.trace4cats.model.TraceHeaders
import isdb.models.users._

import dev.profunktor.auth.jwt._
import io.circe.Decoder
import io.circe.syntax._
import io.circe.parser.{decode => jsonDecode}
import pdi.jwt._

import java.util.UUID
import isdb.service.Security
import dev.profunktor.auth.JwtAuthMiddleware
import cats.data.OptionT
import dev.profunktor.auth.JwtPrivateKey
import org.http4s.server.AuthMiddleware
import pdi.jwt.algorithms.JwtHmacAlgorithm

package object http {
  implicit val traceIO: Trace[IO] = new Trace[IO] {
    def put(key: String, value: AttributeValue): IO[Unit] = IO.unit

    def putAll(fields: (String, AttributeValue)*): IO[Unit] = IO.unit

    def span[A](name: String, kind: SpanKind, errorHandler: ErrorHandler)(fa: IO[A]): IO[A] = fa

    def headers(toHeaders: ToHeaders): IO[TraceHeaders] = IO.pure(TraceHeaders.empty)

    def setStatus(status: SpanStatus): IO[Unit] = IO.unit

    def traceId: IO[Option[String]] = IO.pure(Some(UUID.randomUUID().toString()))

  }

  val tokenSecret = "test"

  private val userDataStub: UserData = UserData(
    jti = UUID.randomUUID(),
    iss = "test",
    aud = "test",
    sub = UUID.randomUUID(),
    typ = "test",
    azp = "test",
    sessionState = UUID.randomUUID(),
    acr = "test",
    realmAccess = Roles(roles = Nil),
    resourceAccess = Map.empty,
    scope = "test",
    emailVerified = true,
    name = "test test",
    preferredUsername = "testUser",
    givenName = "test",
    familyName = "test"
  )

  def makeTestSecurity(allowAccess: Boolean): Security[IO] = new Security[IO] {
    def middlewareWithRole(requiredRole: String Refined NonEmpty): AuthMiddleware[IO, UserData] =
      JwtAuthMiddleware[IO, UserData](
        JwtAuth.hmac(tokenSecret),
        tkn => {
          claim => {
            if (allowAccess) {
              IO.pure(Some(userDataStub))
            } else {
              IO.pure(None)
            }
          }
        }
      )

    def authenticatedMiddleware: AuthMiddleware[IO, UserData] = JwtAuthMiddleware[IO, UserData](
      JwtAuth.hmac(tokenSecret),
      tkn =>
        claim => {
          if (allowAccess) {
            IO.pure(Some(userDataStub))
          } else {
            IO.pure(None)
          }
        }
    )
  }

  def createToken: String = {

    val claim: JwtClaim = JwtClaim(s"""
    |{
    |  "jti": "${UUID.randomUUID()}",
    |  "iss": "http://localhost:8080/auth/realms/dev",
    |  "aud": "account",
    |  "sub": "${UUID.randomUUID()}",
    |  "typ": "Bearer",
    |  "azp": "isdb-app",
    |  "session_state": "${UUID.randomUUID()}",
    |  "acr": "1",
    |  "realm_access": {
    |    "roles": [
    |      "default-roles-dev",
    |      "offline_access",
    |      "uma_authorization"
    |    ]
    |  },
    |  "resource_access": {
    |    "isdb-app": {
    |      "roles": []
    |    },
    |    "account": {
    |      "roles": [
    |        "manage-account",
    |        "manage-account-links",
    |        "view-profile"
    |      ]
    |    }
    |  },
    |  "scope": "profile email",
    |  "email_verified": true,
    |  "name": "isdb basic user",
    |  "preferred_username": "isdb.basic",
    |  "given_name": "isdb",
    |  "family_name": "basic user"
    |}""".stripMargin)
    val alg: JwtHmacAlgorithm = JwtAlgorithm.HS256
    val tkn = jwtEncode[IO](claim, JwtSecretKey(tokenSecret), alg).unsafeRunSync()
    s"Bearer ${tkn.value}"
  }
}
