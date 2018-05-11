package services

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID

import javax.inject.Inject
import model._
import play.api.libs.json.{JsArray, JsObject, Json}

class PrepareUploadService @Inject()() {
  case class Policy(json: JsObject) {
    import utils.Implicits.Base64StringOps
    def asBase64String(): String = Json.stringify(json).base64encode
  }

  def prepareUpload(settings: UploadSettings, uploadUrl: String): PreparedUpload = {
    val reference = generateReference()
    val policy = toPolicy(settings)

    PreparedUpload(
      reference = reference,
      uploadRequest = UploadFormTemplate(
        href = uploadUrl,
        fields = Map(
          "X-Amz-Algorithm"         -> "AWS4-HMAC-SHA256",
          "X-Amz-Expiration"        -> expiration(),
          "X-Amz-Signature"         -> "xxxx",
          "X-Amz-Date"              -> "20111015T080000Z",
          "key"                     -> reference.value,
          "acl"                     -> "private",
          "X-Amz-Credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-meta-callback-url" -> settings.callbackUrl,
          "policy"                  -> policy.asBase64String
        )
      )
    )
  }

  private def generateReference(): Reference = Reference(UUID.randomUUID().toString)

  private def expiration(): String = Instant.now().plus(1, DAYS).toString

  private def toPolicy(settings: UploadSettings): Policy = {
    val json = Json.obj(
      "conditions" -> JsArray(
        Seq(
          Json.arr(
            "content-length-range",
            settings.minimumFileSize,
            settings.maximumFileSize
          )
        )
      )
    )
    Policy(json)
  }
}
