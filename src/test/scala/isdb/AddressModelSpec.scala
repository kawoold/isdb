package isdb

import cats.effect.IO
import cats.implicits.{catsSyntaxEq => _}
import org.scalacheck.Prop.forAll
import isdb.models.address._
import isdb.generators.AddressGenerator.genAddress
import utils.PureTestSuite

class AddressModelSpec extends PureTestSuite {

  forAll(genAddress) { addr: Address =>
    spec("Address states should consist of 2 capital letters")(IO.pure {
      val stateStr = addr.state.value
      assert(stateStr.size === 2 && stateStr.toUpperCase === stateStr)
    })

    spec("Address zipcodes should be in the format XXXXX or XXXXX-YYYY")(IO.pure {
      val zipcode = addr.zipcode.value
      val expectedFormat = "^[0-9]{5}(-[0-9]{4})?$".r
      assert(expectedFormat.matches(zipcode), zipcode)
    })
  }
}
