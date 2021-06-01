package utils

import cats.Eq
import org.scalactic._

final class CatsEquivalence[A](ev: Eq[A]) extends Equivalence[A] {
  def areEquivalent(a: A, b: A): Boolean = ev.eqv(a, b)
}

trait LowPriorityCatsConstraints extends TripleEquals {
  implicit def lowPriorityConstraint[A, B](implicit ev1: Eq[B], ev2: A => B): CanEqual[A, B] =
    new TripleEqualsSupport.AToBEquivalenceConstraint[A, B](new CatsEquivalence(ev1), ev2)
}

trait CatsEquality extends LowPriorityCatsConstraints {
  override def convertToEqualizer[T](left: T): Equalizer[T] = super.convertToEqualizer[T](left)

  implicit override def convertToCheckingEqualizer[T](left: T): CheckingEqualizer[T] = new CheckingEqualizer[T](left)

  override def unconstrainedEquality[A, B](implicit equalityOfA: Equality[A]): CanEqual[A, B] =
    super.unconstrainedEquality[A, B]

  implicit def bToAConstraint[A, B](implicit ev1: Eq[A], ev2: B => A): CanEqual[A, B] =
    new TripleEqualsSupport.BToAEquivalenceConstraint[A, B](new CatsEquivalence(ev1), ev2)
}
