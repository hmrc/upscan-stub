package model

import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.play.test.UnitSpec

class ContentLengthRangeSpec extends UnitSpec with Matchers with GivenWhenThen {
  "ContentLengthRange extractor" should {
    "extract min/max values" in {
      val policy = "{\"conditions\":[[\"content-length-range\",10,99]]}"

      ContentLengthRange.extract(policy) shouldBe Some(ContentLengthRange(Some(10),Some(99)))
    }

    "extract min value" in {
      val policy = "{\"conditions\":[[\"content-length-range\",10,null]]}"

      ContentLengthRange.extract(policy) shouldBe Some(ContentLengthRange(Some(10),None))
    }

    "extract max value" in {
      val policy = "{\"conditions\":[[\"content-length-range\",null,99]]}"

      ContentLengthRange.extract(policy) shouldBe Some(ContentLengthRange(None,Some(99)))
    }

    "extract None when condition name is not present in array" in {
      val policy = "{\"conditions\":[[\"blah\",null,99]]}"

      ContentLengthRange.extract(policy) shouldBe None
    }

    "extract None for min/max when policy json is wrong schema" in {
      val policy = "{\"blah\":null}"

      ContentLengthRange.extract(policy) shouldBe None
    }
  }
}
