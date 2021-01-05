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

package services

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class VirusScannerSpec extends AnyWordSpec with Matchers {

  val eicarSignature = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"

  "Virus scanner" should {

    val virusScanner = new VirusScanner

    "treat file without EICAR signature as clean" in {

      val storedFile = StoredFile("TEST FILE".getBytes)

      virusScanner.checkIfClean(storedFile) shouldBe Clean

    }

    "detect EICAR test signature as a virus" in {

      val storedFile = StoredFile(s"BEFORE $eicarSignature AFTER".getBytes)

      virusScanner.checkIfClean(storedFile) shouldBe VirusFound("Eicar-Test-Signature")

    }
  }

}
