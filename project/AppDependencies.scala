import sbt._

object AppDependencies {
  val bootstrapVersion = "10.1.0"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30" % bootstrapVersion,
    "commons-io"             %  "commons-io"                % "2.20.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"   % bootstrapVersion  % Test
  )
}
