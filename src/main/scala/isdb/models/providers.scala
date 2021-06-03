package isdb.models

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.meta.Meta
import enumeratum._
import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Url
import io.janstenpickle.trace4cats.model.AttributeValue

import java.util.UUID
import scala.util.Try

object providers {
  sealed trait ServiceType extends EnumEntry
  object ServiceType extends Enum[ServiceType] with CirceEnum[ServiceType] {
    def values: IndexedSeq[ServiceType] = findValues

    case object Cable extends ServiceType
    case object Dsl extends ServiceType
    case object Satellite extends ServiceType
    case object Other extends ServiceType

    def toEnum(s: ServiceType): String = s match {
      case Cable     => "cable"
      case Dsl       => "dsl"
      case Satellite => "satellite"
      case Other     => "other"
    }
  }

  implicit val ServiceTypeMeta: Meta[ServiceType] =
    pgEnumStringOpt("service_type", ServiceType.withNameInsensitiveOption, ServiceType.toEnum)

  type CreateProviderRequest = (String Refined NonEmpty, String Refined Url, ServiceType)

  implicit class CreateProviderConversions(req: CreateProviderRequest) {
    def toTraceable: AttributeValue.StringList = AttributeValue.StringList(
      NonEmptyList.of(
        req._1.value,
        req._2.value,
        ServiceType.toEnum(req._3)
      )
    )
  }

  final case class Provider(id: UUID, name: String Refined NonEmpty, url: String Refined Url, serviceType: ServiceType)

  type ProviderDTO = (String, String, String, ServiceType)

  implicit class ProviderConversions(dto: ProviderDTO) {
    def toProvider: ValidatedNel[String, Provider] = {
      (
        Validated.fromTry(Try(UUID.fromString(dto._1))).leftMap(_ => NonEmptyList.one("Invalid ID")),
        Validated.fromEither(refineV[NonEmpty](dto._2)).leftMap(_ => NonEmptyList.one("Invalid Name")),
        Validated.fromEither(refineV[Url](dto._3)).leftMap(_ => NonEmptyList.one("Invalid URL")),
        Validated.valid[NonEmptyList[String], ServiceType](dto._4)
      ).mapN(Provider.apply _)
    }
  }
}
