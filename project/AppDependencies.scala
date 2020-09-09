import sbt._

object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  val compile = Seq(
    ws,
    "uk.gov.hmrc"               %% "bootstrap-backend-play-27" % "2.24.0",
    "org.apache.httpcomponents" %  "httpclient"                % "4.5.11"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc"            %% "service-integration-test"    % "0.12.0-play-27"    % scope,
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % scope,
    "com.typesafe.play"      %% "play-ws"                     % PlayVersion.current % scope,
    "org.mockito"            %% "mockito-scala"               % "1.13.1"            % scope,
    "commons-io"             % "commons-io"                   % "2.6"               % scope,
    "org.scalacheck"         %% "scalacheck"                  % "1.13.4"            % scope,
    "com.github.tomakehurst" %  "wiremock-jre8"               % "2.21.0"            % scope,
    "org.scalatest"          %% "scalatest"                   % "3.1.1"             % scope,
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.5.0"             % scope,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3"             % scope,
    "com.vladsch.flexmark"   %  "flexmark-all"                % "0.35.10"           % scope
  )
}
