package isdb.generators

import org.scalacheck.Gen
import eu.timepit.refined.api._
import isdb.models.address._
import java.util.UUID
import isdb.http.AddressHttpRoutes

object AddressGenerator {

  val addressComponent: Gen[AddressComponent]= for {
    longName <- nonEmptyStringGen
    shortName <- nonEmptyStringGen
    types <- Gen.listOf(nonEmptyStringGen)
  } yield AddressComponent(longName, shortName, types)

  val locationPoint: Gen[LocationPoint] = for {
    lat <- Gen.double
    lon <- Gen.double
  } yield LocationPoint(lat, lon)

  val locationBound: Gen[LocationBounds] = for {
    ne <- locationPoint
    sw <- locationPoint
  } yield LocationBounds(ne, sw)

  val locationGeometry: Gen[LocationGeometry] = for {
    bounds <- locationBound
    location <- locationPoint
    locType <- nonEmptyStringGen
    viewport <- locationBound
  } yield LocationGeometry(bounds, location, locType, viewport)

  val geocodingResult: Gen[GeocodingResult] = for {
    addrComp <- Gen.listOf(addressComponent)
    formattedAddr <- nonEmptyStringGen
    geometry <- locationGeometry
    partialMatch <- Gen.option(Gen.oneOf(true, false))
    placeId <- nonEmptyStringGen
    types <- Gen.listOf(nonEmptyStringGen)
  } yield GeocodingResult(addrComp, formattedAddr, geometry, partialMatch, placeId, types)

  val geocodingResults: Gen[GeocodingResults] = for {
    results <- Gen.listOf(geocodingResult)
    status <- nonEmptyStringGen
  } yield GeocodingResults(results, status)

  implicit val stateGen: Gen[State] =
    Gen.listOfN[Char](2, Gen.alphaUpperChar).map(_.mkString).map(str => RefType.applyRef[State](str).toOption.get)

  implicit val zipGen: Gen[ZipCode] = for {
    firstFive <- Gen.listOfN[Char](5, Gen.numChar).map(_.mkString)
    lastFour <- Gen.listOfN[Char](4, Gen.numChar).map(_.mkString)
    shortZip = RefType.applyRef[ZipCode](firstFive).toOption.get
    longZip = RefType.applyRef[ZipCode](s"$firstFive-$lastFour").toOption.get
    result <- Gen.oneOf(shortZip, longZip)
  } yield result

  val genAddressDto: Gen[AddressDTO] = for {
    idStr <- Gen.frequency(4 -> UUID.randomUUID().toString, 1 -> "", 1 -> "Not a real GUID")
    line1Str <- Gen.frequency(4 -> Gen.alphaStr, 1 -> Gen.const(""))
    line2Str <- Gen.oneOf(Gen.option(Gen.alphaStr), Gen.const(Some("")))
    stateStr <- Gen.frequency(
      4 -> stateGen.map(_.value),
      1 -> Gen.const(""),
      1 -> Gen.const("NotAValidState"),
      1 -> Gen.const("Va")
    )
    zipStr <- Gen.frequency(
      4 -> zipGen.map(_.value),
      1 -> Gen.const(""),
      1 -> Gen.const("1234"),
      1 -> Gen.const("NotAZip")
    )
    geoResults <- Gen.option(geocodingResults)
  } yield (idStr, line1Str, line2Str, stateStr, zipStr, geoResults)

  val genAddress: Gen[Address] = for {
    id <- Gen.uuid
    line1 <- nonEmptyStringGen
    line2 <- Gen.option(nonEmptyStringGen)
    state <- stateGen
    zip <- zipGen
    geoResults <- Gen.option(geocodingResults)
  } yield Address(id, line1, line2, state, zip, geoResults)

  val genValidCreationRequests: Gen[AddressHttpRoutes.CreateAddress] = for {
    line1 <- nonEmptyStringGen.map(_.value)
    line2 <- Gen.option(nonEmptyStringGen.map(_.value))
    state <- stateGen.map(_.value)
    zip <- zipGen.map(_.value)
  } yield AddressHttpRoutes.CreateAddress(line1, line2, state, zip)

  val genInvalidCreationRequests: Gen[AddressHttpRoutes.CreateAddress] = for {
    line1 <- Gen.alphaStr
    line2 <- Gen.option(Gen.alphaStr)
    state <- Gen.alphaStr.suchThat(_.size != 2)
    zip <- Gen.alphaStr
  } yield AddressHttpRoutes.CreateAddress(line1, line2, state, zip)
}
