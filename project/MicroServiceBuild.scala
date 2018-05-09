import sbt._

object MicroServiceBuild extends Build with MicroService {
  import scala.util.Properties.envOrElse

  val appName = "upscan-stub"
  val appVersion = envOrElse("UPSCAN_STUB_VERSION", "999-SNAPSHOT")

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.sbt.PlayImport._
  import play.core.PlayVersion


  val compile = Seq(

    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25" % "1.5.0"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % "test,it",
    "org.scalatest" %% "scalatest" % "2.2.6" % "test,it",
    "org.pegdown" % "pegdown" % "1.4.2" % "test,it",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % "test,it"
  )

  def apply() = compile ++ test
}
