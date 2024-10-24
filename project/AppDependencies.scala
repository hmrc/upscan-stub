import sbt._

object AppDependencies {
  import play.sbt.PlayImport.ws

  val bootstrapVersion = "9.5.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"               %% "bootstrap-backend-play-30" % bootstrapVersion,
    "org.apache.httpcomponents" %  "httpclient"                % "4.5.14",
    "commons-io"                %  "commons-io"                % "2.15.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"   % bootstrapVersion  % Test
  )
}
