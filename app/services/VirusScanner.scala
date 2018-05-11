package services

sealed trait ScanningResult
case object Clean extends ScanningResult
case class VirusFound(details: String) extends ScanningResult

class VirusScanner {

  private val eicarSignature = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"

  def checkIfClean(file: StoredFile): ScanningResult =
    if (file.body.containsSlice(eicarSignature)) {
      VirusFound("Eicar-Test-Signature")
    } else {
      Clean
    }
}
