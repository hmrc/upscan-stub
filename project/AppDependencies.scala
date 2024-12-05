import sbt._

object AppDependencies {
  import play.sbt.PlayImport.ws

  val bootstrapVersion = "9.5.0"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "commons-io"             %  "commons-io"                % "2.18.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"   % bootstrapVersion  % Test
  )
}
