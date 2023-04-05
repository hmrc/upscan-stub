import sbt._

object AppDependencies {
  import play.sbt.PlayImport.ws

  val bootstrapVersion = "7.15.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"               %% "bootstrap-backend-play-28" % bootstrapVersion,
    "org.apache.httpcomponents" %  "httpclient"                % "4.5.13",
    "commons-io"                %  "commons-io"                % "2.8.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"   % bootstrapVersion  % "test,it",
    "org.mockito"            %% "mockito-scala-scalatest"  % "1.16.46"         % "test,it"
  )
}
