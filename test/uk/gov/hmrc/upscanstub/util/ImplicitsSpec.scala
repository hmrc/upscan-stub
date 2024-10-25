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

package uk.gov.hmrc.upscanstub.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Base64StringUtilsSpec extends AnyWordSpec with Matchers:
  "Base64StringOps" should:
    "base64 encode a String" in:
      Base64StringUtils.base64encode("Hello, World") shouldBe "SGVsbG8sIFdvcmxk"

    "base64 decode a String" in:
      Base64StringUtils.base64decode("SGVsbG8sIFdvcmxk") shouldBe "Hello, World"
