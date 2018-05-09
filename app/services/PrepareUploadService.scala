package services

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID

import javax.inject.Inject
import models._

class PrepareUploadService @Inject()() {

  def prepareUpload(settings: UploadSettings, uploadUrl: String): PreparedUpload = {
    val reference = generateReference()
    PreparedUpload(
      reference = reference,
      uploadRequest = UploadFormTemplate(
        href = uploadUrl,
        fields = Map(
          "X-Amz-Algorithm"         -> "AWS4-HMAC-SHA256",
          "X-Amz-Expiration"        -> expiration(),
          "X-Amz-Signature"         -> "xxxx",
          "key"                     -> reference.value,
          "acl"                     -> "private",
          "X-Amz-Credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-meta-callback-url" -> settings.callbackUrl,
          "policy"                  -> "xxxxxxxx=="
        )
      )
    )
  }

  private def generateReference(): Reference = Reference(UUID.randomUUID().toString)

  private def expiration(): String = Instant.now().plus(1, DAYS).toString
}
