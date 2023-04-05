/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utils

object ApplicativeHelpers {

  def product[T1, T2, E](
    e1: Either[Iterable[E], T1],
    e2: Either[Iterable[E], T2]
  ): Either[Iterable[E], (T1, T2)] =
    if (e1.isRight && e2.isRight)
      Right((e1.toOption.get, e2.toOption.get))
    else
      Left((e1.left.toSeq ++ e2.left.toSeq).flatten)
}
