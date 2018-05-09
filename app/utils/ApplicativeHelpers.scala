package utils

object ApplicativeHelpers {

  def product[T1, T2, E](
    e1: Either[Traversable[E], T1],
    e2: Either[Traversable[E], T2]): Either[Traversable[E], (T1, T2)] =
    if (e1.isRight && e2.isRight) {
      Right((e1.right.get, e2.right.get))
    } else {
      Left((e1.left.toSeq ++ e2.left.toSeq).flatten)
    }

}
