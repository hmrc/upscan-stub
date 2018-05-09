package models

case class UploadValues(
                       algorithm: String,
                       credential: String,
                       date: String,
                       policy: String,
                       signature: String,
                       acl: String,
                       key: String
                     )