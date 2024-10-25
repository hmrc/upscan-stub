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

package uk.gov.hmrc.upscanstub.model

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ContentLengthRangeSpec
  extends AnyWordSpec
     with Matchers
     with GivenWhenThen:

  "ContentLengthRange extractor" should:
    "extract min/max values" in:
      val policy = "{\"conditions\":[[\"content-length-range\",10,99]]}"
      ContentLengthRange.extract(policy) shouldBe Some(ContentLengthRange(Some(10),Some(99)))

    "extract min value" in:
      val policy = "{\"conditions\":[[\"content-length-range\",10,null]]}"
      ContentLengthRange.extract(policy) shouldBe Some(ContentLengthRange(Some(10),None))

    "extract max value" in:
      val policy = "{\"conditions\":[[\"content-length-range\",null,99]]}"
      ContentLengthRange.extract(policy) shouldBe Some(ContentLengthRange(None,Some(99)))

    "extract None when condition name is not present in array" in:
      val policy = "{\"conditions\":[[\"blah\",null,99]]}"
      ContentLengthRange.extract(policy) shouldBe None

    "extract None for min/max when policy json is wrong schema" in:
      val policy = "{\"blah\":null}"
      ContentLengthRange.extract(policy) shouldBe None
