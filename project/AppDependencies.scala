import sbt._

object AppDependencies {
  val bootstrapVersion = "9.11.0"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "commons-io"             %  "commons-io"                % "2.18.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"   % bootstrapVersion  % Test
  )
}
