package isdb.models

import java.util.UUID
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import io.circe.syntax._
import io.circe._
import io.circe.refined._
import io.circe.derivation._

object users {
  case class Roles(roles: List[String Refined NonEmpty])
  case class UserData(
      jti: UUID,
      iss: String Refined NonEmpty,
      aud: String Refined NonEmpty,
      sub: UUID,
      typ: String Refined NonEmpty,
      azp: String Refined NonEmpty,
      sessionState: UUID,
      acr: String Refined NonEmpty,
      realmAccess: Roles,
      resourceAccess: Map[String Refined NonEmpty, Roles],
      scope: String Refined NonEmpty,
      emailVerified: Boolean,
      name: String Refined NonEmpty,
      preferredUsername: String Refined NonEmpty,
      givenName: String Refined NonEmpty,
      familyName: String Refined NonEmpty
  ) {
    val availableRoles: List[String Refined NonEmpty] = realmAccess.roles ++ resourceAccess.values.flatMap(_.roles)

    def hasRole(expected: String): Boolean = availableRoles.map(_.value) contains expected
  }

  implicit val rolesCodec: Codec[Roles] = deriveCodec(derivation.renaming.snakeCase)
  implicit val userCodec: Codec[UserData] = deriveCodec(derivation.renaming.snakeCase)
}
