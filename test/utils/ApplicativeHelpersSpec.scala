package utils

import uk.gov.hmrc.play.test.UnitSpec

class ApplicativeHelpersSpec extends UnitSpec {
  import ApplicativeHelpers._

  "product function" should {
    "allow to combine two right Either values into tuple" in {
      product(Right("VAL1"), Right("VAL2")) shouldBe Right("VAL1", "VAL2")
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
