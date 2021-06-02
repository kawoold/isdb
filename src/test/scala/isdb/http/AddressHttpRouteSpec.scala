package isdb.http

import utils._
import isdb.models.address._
import isdb.repository.algebra._
import isdb.generators.AddressGenerator
import isdb.service.AddressService
import cats.effect.IO
import cats.syntax.all._
import cats.implicits._
import org.scalacheck.Gen
import org.http4s._
import org.http4s.Method._
import org.http4s.client.dsl.io._
import org.scalacheck.Prop.forAll
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.circe.CirceInstances
import io.circe.generic.auto._
import io.circe.refined._
import io.odin.Logger
import org.scalatest.compatible.Assertion

import java.util.UUID
import eu.timepit.refined.api.RefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.auto._
import isdb.service.GeocodingFinder
import isdb.service.Security
import org.http4s.server.AuthMiddleware
import isdb.models.users._

class AddressHttpRouteSpec extends HttpTestSuite {

  implicit val logger: Logger[IO] = Logger.noop
  implicit val errorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]
  implicit def createEncoder[A: Encoder]: EntityEncoder[IO, A] = jsonEncoderOf[IO, A]

  def testAddressRepository(addresses: List[Address], newId: () => UUID): AddressRepository[IO] =
    new AddressRepository[IO] {
      var repoAddresses = addresses
      def createAddress(addr: CreateAddressRequest): IO[Either[String, Address]] =
        IO.pure({
          val newAddress = Address(newId(), addr._1, addr._2, addr._3, addr._4, addr._5)
          repoAddresses = newAddress :: repoAddresses
          Right(newAddress)
        })

      def findAll: IO[List[Address]] = IO.pure(repoAddresses)

      def find(id: UUID): IO[Option[Address]] = IO.pure(repoAddresses.find(_.id == id))
    }

  def testGeoFinder(geoResult: GeocodingResults): GeocodingFinder[IO] = new GeocodingFinder[IO] {
    def lookupGeocode(address: String): IO[GeocodingResults] = IO.pure(geoResult)
  }

  forAll(Gen.listOfN(5, AddressGenerator.genAddress), AddressGenerator.geocodingResults) { (addresses, geoResults) =>
    implicit val allowedSec: Security[IO] = makeTestSecurity(true)
    spec("Address lookup should return all results") {
      val addressRoutes: HttpRoutes[IO] =
        new AddressHttpRoutes[IO](
          new AddressService(testAddressRepository(addresses, () => Gen.uuid.sample.get), testGeoFinder(geoResults))
        ).routes
      GET(Uri.uri("/address")).flatMap { req =>
        assertHttp(addressRoutes, req.putHeaders(Header("Authorization", createToken)))(Status.Ok, addresses)
      }
    }
  }

  spec("Address lookup should return Forbidden if the user is not authorized") {
    implicit val deniedSec: Security[IO] = makeTestSecurity(false)
    val addressRoutes: HttpRoutes[IO] = new AddressHttpRoutes[IO](
      new AddressService(
        testAddressRepository(Nil, () => Gen.uuid.sample.get),
        testGeoFinder(AddressGenerator.geocodingResults.sample.get)
      )
    ).routes
    GET(Uri.uri("/address")).flatMap { req =>
      assertHttpStatus(addressRoutes, req.putHeaders(Header("Authorization", createToken)))(Status.Forbidden)
    }
  }

  spec("Address lookup should return Forbidden if the user does not include an authorization header") {
    implicit val deniedSec: Security[IO] = makeTestSecurity(false)
    val addressRoutes: HttpRoutes[IO] = new AddressHttpRoutes[IO](
      new AddressService(
        testAddressRepository(Nil, () => Gen.uuid.sample.get),
        testGeoFinder(AddressGenerator.geocodingResults.sample.get)
      )
    ).routes
    GET(Uri.uri("/address")).flatMap { req =>
      assertHttpStatus(addressRoutes, req)(Status.Forbidden)
    }
  }

  forAll(AddressGenerator.geocodingResults) { geoResults =>
    implicit val allowedSec: Security[IO] = makeTestSecurity(true)
    spec("Address lookup should return an empty list if no addresses have been added") {
      val addressRoutes: HttpRoutes[IO] =
        new AddressHttpRoutes[IO](
          new AddressService(testAddressRepository(Nil, () => Gen.uuid.sample.get), testGeoFinder(geoResults))
        ).routes

      GET(Uri.uri("/address")).flatMap { req =>
        assertHttp(addressRoutes, req.putHeaders(Header("Authorization", createToken)))(Status.Ok, List.empty[Address])
      }
    }
  }

  forAll(AddressGenerator.genValidCreationRequests, AddressGenerator.geocodingResults) { (addr, geoResults) =>
    implicit val allowedSec: Security[IO] = makeTestSecurity(true)
    spec("Valid addresses should be inserted") {
      val validNewIds = Gen.uuid.sample.get :: Nil
      val addressRoutes: HttpRoutes[IO] =
        new AddressHttpRoutes[IO](
          new AddressService(testAddressRepository(Nil, () => validNewIds.head), testGeoFinder(geoResults))
        ).routes

      POST(addr, Uri.uri("/address")).flatMap { req =>
        assertResult[Address](addressRoutes, req.putHeaders(Header("Authorization", createToken)))(
          Status.Ok,
          result => {
            assert(
              result.id === validNewIds.head &&
                result.line1.value === addr.line1 &&
                result.line2.map(_.value) === addr.line2 &&
                result.state.value === addr.state &&
                result.zipcode.value === addr.zipcode
            )
          }
        )
      }
    }
  }

  forAll(AddressGenerator.genInvalidCreationRequests, AddressGenerator.geocodingResults) { (addr, geoResults) =>
    implicit val allowedSec: Security[IO] = makeTestSecurity(true)
    spec("Invalid addresses should fail with a bad request") {
      val routes = new AddressHttpRoutes[IO](
        new AddressService(testAddressRepository(Nil, () => UUID.randomUUID()), testGeoFinder(geoResults))
      ).routes
      POST(addr, Uri.uri("/address")).flatMap { req =>
        assertHttpStatus(routes, req.putHeaders(Header("Authorization", createToken)))(Status.UnprocessableEntity)
      }
    }
  }
}
