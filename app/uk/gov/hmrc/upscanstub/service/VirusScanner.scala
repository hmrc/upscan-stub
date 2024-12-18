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

package uk.gov.hmrc.upscanstub.service

enum ScanningResult:
  case Clean                       extends ScanningResult
  case VirusFound(details: String) extends ScanningResult

class VirusScanner:

  private val eicarSignature =
    "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"

  def checkIfClean(file: StoredFile): ScanningResult =
    if file.body.containsSlice(eicarSignature.getBytes) then
      ScanningResult.VirusFound("Eicar-Test-Signature")
    else
      ScanningResult.Clean
