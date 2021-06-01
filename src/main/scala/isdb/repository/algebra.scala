package isdb.repository

import isdb.models.address._
import isdb.models.providers._
import java.util.UUID

object algebra {
  trait AddressRepository[F[_]] {
      def createAddress(addr: CreateAddressRequest): F[Either[String, Address]]

      def findAll: F[List[Address]]

      def find(id: UUID): F[Option[Address]]
  }

  trait ProviderRepository[F[_]] {
    def createProvider(provider: CreateProviderRequest): F[Either[String, Provider]]

    def findAll: F[List[Provider]]

    def find(id: UUID): F[Option[Provider]]
  }
}
