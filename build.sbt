import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"

lazy val microservice = Project("upscan-stub", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(
  //  scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s",
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(PlayKeys.playDefaultPort := 9570)

lazy val it =
  (project in file("it"))
    .enablePlugins(PlayScala)
    .dependsOn(microservice % "test->test")
    .settings(DefaultBuildSettings.itSettings())
