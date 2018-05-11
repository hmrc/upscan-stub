package services

import org.scalatest.Matchers
import uk.gov.hmrc.play.test.UnitSpec

class VirusScannerSpec extends UnitSpec with Matchers {

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
