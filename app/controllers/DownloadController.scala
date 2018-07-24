package controllers

import akka.util.ByteString
import javax.inject.Inject
import model.FileId
import play.api.http.HttpEntity
import play.api.mvc.{Action, ResponseHeader, Result}
import services.FileStorageService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

class DownloadController @Inject()(storageService: FileStorageService) extends BaseController {

  def download(fileId: String) = Action {
    (for {
      source <- storageService.get(FileId(fileId))
    } yield {
      Result(
        header = ResponseHeader(200, Map.empty),
        body   = HttpEntity.Strict(ByteString(source.body), None)
      )
    }) getOrElse NotFound

  }

}
