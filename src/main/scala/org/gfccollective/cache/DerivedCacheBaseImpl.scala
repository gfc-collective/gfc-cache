package org.gfccollective.cache

import org.gfccollective.logging.Loggable

private[cache] trait DerivedCacheBaseImpl extends CacheBase with Loggable {
  def parent: CacheBase

  override def start() = {
    parent.start()
    this
  }

  override def isStarted = parent.isStarted

  override def shutdown() {}
}
