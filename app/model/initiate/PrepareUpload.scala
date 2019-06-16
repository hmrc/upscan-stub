package model.initiate

trait PrepareUpload {
  def callbackUrl: String
  def toUploadSettings(uploadUrl: String): UploadSettings
}
