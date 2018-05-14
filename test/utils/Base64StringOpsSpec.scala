package utils

import uk.gov.hmrc.play.test.UnitSpec

class Base64StringOpsSpec extends UnitSpec {
  import utils.Implicits.Base64StringOps

  "Base64StringOps" should {
    "base64 encode a String" in {
      val actual: String = "Hello, World".base64encode

      actual shouldBe "SGVsbG8sIFdvcmxk"
    }

    "base64 decode a String" in {
      val actual: String = "SGVsbG8sIFdvcmxk".base64decode

      actual shouldBe "Hello, World"
    }
  }
}
