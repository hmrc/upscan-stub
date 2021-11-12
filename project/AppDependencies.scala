import sbt._

object AppDependencies {
  import play.sbt.PlayImport._

  val bootstrapVersion = "5.16.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"               %% "bootstrap-backend-play-28" % bootstrapVersion,
    "org.apache.httpcomponents" % "httpclient"                 % "4.5.13"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"   % bootstrapVersion  % scope,
    "org.mockito"            %% "mockito-scala-scalatest"  % "1.16.46"         % scope,
    "uk.gov.hmrc"            %% "service-integration-test" % "0.13.0-play-27"  % scope,
    "commons-io"              % "commons-io"               % "2.8.0"           % scope
  )
}
