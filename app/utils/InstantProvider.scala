package utils

import java.time.Instant

trait InstantProvider {
  def now(): Instant
}

class InstantProviderImpl extends InstantProvider {
  override def now(): Instant = Instant.now()
}
