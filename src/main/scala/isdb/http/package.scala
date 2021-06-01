package isdb

import java.util.UUID
import scala.util.Try

package object http {
  object UuidVar {
      def unapply(strVal: String): Option[UUID] = Try(UUID.fromString(strVal)).toOption
  }
}
