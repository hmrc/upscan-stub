package services

import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import java.time.temporal.ChronoUnit.DAYS

import models._

class PrepareUploadService @Inject()() {

  def prepareUpload(settings: UploadSettings): PreparedUpload = {
    PreparedUpload(
      reference = generateReference(),
      uploadRequest = UploadFormTemplate(
        href = "http://localhost:8080/upscan-stub-prototype-simple/upload",
        fields = Map(
          "X-Amz-Algorithm" -> "AWS4-HMAC-SHA256",
          "X-Amz-Expiration" -> expiration(),
          "X-Amz-Signature" -> "xxxx",
          "key" -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
          "acl" -> "private",
          "X-Amz-Credential" -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "policy" -> "xxxxxxxx=="
        )
      )
    )
  }

  private def generateReference(): Reference = Reference(UUID.randomUUID().toString)

  private def expiration(): String = Instant.now().plus(1, DAYS).toString
}
