package com.gilt.gfc.cache

import java.util.concurrent.TimeUnit

import com.gilt.gfc.guava.cache.CacheInitializationStrategy

/**
 * Strongly typed configuration for a commons cache.
 * Takes a base configuration, which provides default parameters, and a sub-config name, which provides
 * overrides (if present).
 *
 * Configuration values (all are mandatory):
 *  - refresh_interval: How long (in refresh_units) to sleep between cache refreshes
 *  - refresh_units: The time unit for refresh_interval (seconds/minutes/hours)
 *  - init_strategy: Whether to initially load the cache asynchronously or synchronously (async/sync)
 *
 * @author kmcgregor
 * @since 7/23/13
 *
 */
trait CacheConfiguration {
  def refreshPeriodMs: Long
  def cacheInitStrategy: CacheInitializationStrategy
}

trait CommonsCacheConfiguration extends CacheConfiguration {

  def baseConfig: Config = SystemConfigFactory.create("commons_cache")

  def overrideSection: Option[String] = None

  lazy val cacheConfig: Config = overrideSection.flatMap(baseConfig.optSubConfig(_)).map(new HierarchicalConfig(_, baseConfig)).getOrElse(baseConfig)

  override lazy val refreshPeriodMs: Long = {
    val refreshInterval = {
      val refreshIntervalTemp = cacheConfig.requiredInt("refresh_interval")
      Preconditions.checkArgument(refreshIntervalTemp > 0, "refresh_interval must be > 0")
      refreshIntervalTemp
    }

    val refreshUnits = TimeUnit.valueOf(cacheConfig.requiredString("refresh_units").toUpperCase)

    TimeUnit.MILLISECONDS.convert(refreshInterval, refreshUnits)
  }

  override lazy val cacheInitStrategy: CacheInitializationStrategy =
    CacheInitializationStrategy.valueOf(cacheConfig.requiredString("init_strategy").toUpperCase)
}
