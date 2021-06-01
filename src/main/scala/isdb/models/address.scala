package isdb.models

import cats.Show
import cats.data.NonEmptyList
import eu.timepit.refined.api.Refined
import eu.timepit.refined._
import eu.timepit.refined.string._
import eu.timepit.refined.collection._
import java.util.UUID
import io.janstenpickle.trace4cats.model.AttributeValue
import eu.timepit.refined.api.RefType
import scala.util.Try
import eu.timepit.refined
import io.circe.{Decoder, Encoder, ObjectEncoder}
import io.circe.syntax._
import io.circe._
import io.circe.refined._
import io.circe.derivation._
import doobie.postgres.circe.json.implicits._
import doobie.util.Get
import doobie.util.Put

object address {
  type State = String Refined MatchesRegex[W.`"[A-Z]{2}"`.T]

  type ZipCode = String Refined MatchesRegex[W.`"[0-9]{5}(-[0-9]{4})?"`.T]

  case class Address(
      id: UUID,
      line1: String Refined NonEmpty,
      line2: Option[String Refined NonEmpty],
      state: State,
      zipcode: ZipCode,
      geoResults: Option[GeocodingResults]
  )

  type AddressDTO = (String, String, Option[String], String, String, Option[GeocodingResults])

  implicit class AddressConversions(dto: AddressDTO) {
    def toAddress: Option[Address] = for {
      id <- Try(UUID.fromString(dto._1)).toOption
      line1 <- refineV[NonEmpty](dto._2).toOption
      line2 = dto._3.map(refineV[NonEmpty](_)).flatMap(_.toOption)
      state <- RefType.applyRef[State](dto._4).toOption
      zip <- RefType.applyRef[ZipCode](dto._5).toOption
    } yield Address(id, line1, line2, state, zip, dto._6)
  }

  type CreateAddressRequest = (String Refined NonEmpty, Option[String Refined NonEmpty], State, ZipCode, Option[GeocodingResults])

  implicit class CreateAddressConversions(req: CreateAddressRequest) {
    def toTraceable: AttributeValue.StringList = AttributeValue.StringList(
      NonEmptyList.of(
        req._1.value,
        req._2.map(_.value).getOrElse(""),
        req._3.value,
        req._4.value
      )
    )

    val formattedAddress: String = List(
      Some(req._1.value),
      req._2.map(_.value),
      Some(req._3.value),
      Some(req._4.value)
    ).flatten.mkString(", ")
  }

  case class AddressComponent(
      longName: String Refined NonEmpty,
      shortName: String Refined NonEmpty,
      types: List[String Refined NonEmpty]
  )

  implicit val addressComponentCodec: Codec[AddressComponent] = deriveCodec(derivation.renaming.snakeCase)

  implicit val addressCompGet: Get[AddressComponent] = pgDecoderGet
  implicit val addressCompPut: Put[AddressComponent] = pgEncoderPut

  case class LocationPoint(lat: Double, lng: Double)

  implicit val pointCodec: Codec[LocationPoint] = deriveCodec(derivation.renaming.snakeCase)

  implicit val pointGet: Get[LocationPoint] = pgDecoderGet
  implicit val pointPut: Put[LocationPoint] = pgEncoderPut

  case class LocationBounds(northeast: LocationPoint, southwest: LocationPoint)

  implicit val boundsCodec: Codec[LocationBounds] = deriveCodec(derivation.renaming.snakeCase)

  implicit val boundsGet: Get[LocationBounds] = pgDecoderGet
  implicit val boundsPut: Put[LocationBounds] = pgEncoderPut

  case class LocationGeometry(
      bounds: LocationBounds,
      location: LocationPoint,
      locationType: String Refined NonEmpty,
      viewport: LocationBounds
  )

  implicit val geometryCodec: Codec[LocationGeometry] = deriveCodec(derivation.renaming.snakeCase)

  implicit val geometryGet: Get[LocationGeometry] = pgDecoderGet
  implicit val geometryPut: Put[LocationGeometry] = pgEncoderPut

  case class GeocodingResult(
      addressComponents: List[AddressComponent],
      formattedAddress: String Refined NonEmpty,
      geometry: LocationGeometry,
      partialMatch: Option[Boolean],
      placeId: String Refined NonEmpty,
      types: List[String Refined NonEmpty]
  )

  implicit val resultCodec: Codec[GeocodingResult] = deriveCodec(derivation.renaming.snakeCase)

  implicit val resultGet: Get[GeocodingResult] = pgDecoderGet
  implicit val resultPut: Put[GeocodingResult] = pgEncoderPut

  case class GeocodingResults(results: List[GeocodingResult], status: String Refined NonEmpty)

  implicit val resultsCodec: Codec[GeocodingResults] = deriveCodec(derivation.renaming.snakeCase)

  implicit val resultsGet: Get[GeocodingResults] = pgDecoderGet
  implicit val resultsPut: Put[GeocodingResults] = pgEncoderPut
}
