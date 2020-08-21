package it.utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait WithWireMock extends BeforeAndAfterEach with BeforeAndAfterAll {
  this: Suite =>

  val wiremockPort: Int

  private lazy val wireMockServer = new WireMockServer(wireMockConfig().port(wiremockPort))

  override def beforeAll() = {
    super.beforeAll
    wireMockServer.start
    WireMock.configureFor(wiremockPort)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop
    super.afterAll
  }

  override def beforeEach() = {
    super.beforeEach
    WireMock.reset
  }
}
