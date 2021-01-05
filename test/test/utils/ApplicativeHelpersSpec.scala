/*
 * Copyright 2021 HM Revenue & Customs
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

package test.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import utils.ApplicativeHelpers


class ApplicativeHelpersSpec extends AnyWordSpec with Matchers {
  import ApplicativeHelpers._

  "product function" should {
    "allow to combine two right Either values into tuple" in {
      product(Right("VAL1"), Right("VAL2")) shouldBe Right("VAL1" -> "VAL2")
    }
    "collect errors for two left Eithers" in {
      product(Left(Seq("ERR1", "ERR1B")), Left(Seq("ERR2"))) shouldBe Left(Seq("ERR1", "ERR1B", "ERR2"))
    }
    "collect errors if only one of Eithers is has left value" in {
      product(Right("VAL1"), Left(Seq("ERR2")))          shouldBe Left(Seq("ERR2"))
      product(Left(Seq("ERR1", "ERR1B")), Right("VAL2")) shouldBe Left(Seq("ERR1", "ERR1B"))
    }
  }

}
