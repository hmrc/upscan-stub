import sbt._

object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._


  val bootstrapPlayVersion = "1.6.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"               %% "bootstrap-play-26" % bootstrapPlayVersion,
    "org.apache.httpcomponents" %  "httpclient"        % "4.5.11"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc"            %% "bootstrap-play-26"           % bootstrapPlayVersion % scope classifier "tests",
    "uk.gov.hmrc"            %% "service-integration-test"    % "0.10.0-play-26"    % scope,
    "uk.gov.hmrc"            %% "http-verbs-test"             % "1.8.0-play-26"     % scope,
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % scope,
    "com.typesafe.play"      %% "play-ws"                     % PlayVersion.current % scope,
    "org.mockito"            %% "mockito-scala"               % "1.13.1"            % scope,
    "commons-io"             % "commons-io"                   % "2.6"               % scope,
    "org.scalacheck"         %% "scalacheck"                  % "1.13.4"            % scope,
    "com.github.tomakehurst" %  "wiremock-jre8"               % "2.21.0"            % scope,
    "org.scalatest"          %% "scalatest"                   % "3.1.0"             % scope,
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.5.0"             % scope,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "3.1.3"             % scope,
    "com.vladsch.flexmark"  %   "flexmark-all"                % "0.35.10"           % scope,

  )


}
