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

import java.util.Base64

// TODO just use helpers
object Implicits:

  implicit class Base64StringOps(input: String):

    def base64encode(): String =
      val encodedBytes = Base64.getEncoder.encode(input.getBytes("UTF-8"))
      new String(encodedBytes).replaceAll(System.lineSeparator, "")

    def base64decode(): String =
      new String(Base64.getDecoder.decode(input))
