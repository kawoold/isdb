package isdb

import org.scalacheck.Gen
import eu.timepit.refined.api._
import eu.timepit.refined.collection.NonEmpty

package object generators {
  val nonEmptyStringGen: Gen[String Refined NonEmpty] =
    Gen.alphaStr.suchThat(_.nonEmpty).map(str => RefType.applyRef[String Refined NonEmpty](str).toOption.get)
}
