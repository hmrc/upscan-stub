package controllers

import javax.inject.Inject

import akka.util.ByteString
import model.Reference
import play.api.http.HttpEntity
import play.api.mvc.{Action, ResponseHeader, Result}
import services.FileStorageService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext


class DownloadController @Inject()(storageService: FileStorageService)(implicit ec: ExecutionContext)
  extends BaseController {

  def download(reference: String) = Action {
    (for {
      source <- storageService.get(Reference(reference))
    } yield {
      Result(
        header = ResponseHeader(200, Map.empty),
        body = HttpEntity.Strict(ByteString(source.body), None)
      )
    }) getOrElse NotFound

  }

}