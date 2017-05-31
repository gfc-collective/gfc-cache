package com.gilt.gfc.cache

import com.gilt.gfc.logging.Loggable

private[cache] trait DerivedCacheBaseImpl extends CacheBase with FutureTiming with Loggable {
  def parent: CacheBase

  override def start() = {
    parent.start()
    this
  }

  override def isStarted = parent.isStarted

  override def shutdown() {}
}
