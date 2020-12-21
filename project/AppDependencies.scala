import sbt._

object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile = Seq(
    ws,
    "uk.gov.hmrc"               %% "bootstrap-backend-play-27" % "3.2.0",
    "org.apache.httpcomponents" %  "httpclient"                % "4.5.13"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc"            %% "service-integration-test"    % "0.13.0-play-27"    % scope,
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % scope,
    "com.typesafe.play"      %% "play-ws"                     % PlayVersion.current % scope,
    "org.mockito"            %% "mockito-scala"               % "1.16.3"            % scope,
    "commons-io"             % "commons-io"                   % "2.8.0"             % scope,
    "org.scalacheck"         %% "scalacheck"                  % "1.15.2"            % scope,
    "com.github.tomakehurst" %  "wiremock-jre8"               % "2.27.2"            % scope,
    "org.scalatest"          %% "scalatest"                   % "3.1.4"             % scope,
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.5.0"             % scope,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3"             % scope,
    "com.vladsch.flexmark"   %  "flexmark-all"                % "0.35.10"           % scope
  )
}
