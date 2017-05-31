package com.gilt.gfc.cache

import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite

/**
 * @author kmcgregor
 * @since 7/23/13
 *
 */
@Mock
class CacheConfigurationTest extends TestNGSuite with Matchers {

  @Test
  def testInitStrategy() {
    val cacheConfigAsync = new CommonsCacheConfiguration {
      override lazy val cacheConfig = YamlConfigFactory.loadConfig(
        """init_strategy: "async"
          |refresh_interval: 10
          |refresh_units: "minutes"
        """.stripMargin)
    }

    cacheConfigAsync.cacheInitStrategy shouldBe CacheInitializationStrategy.ASYNC

    val cacheConfigSync = new CommonsCacheConfiguration {
      override lazy val cacheConfig = YamlConfigFactory.loadConfig(
        """init_strategy: "sync"
          |refresh_interval: 10
          |refresh_units: "minutes"
        """.stripMargin)
    }

    cacheConfigSync.cacheInitStrategy shouldBe CacheInitializationStrategy.SYNC
  }

  @Test
  def testRefreshPeriod() {
    an [IllegalArgumentException] should be thrownBy {
      new CommonsCacheConfiguration {
        override lazy val cacheConfig = YamlConfigFactory.loadConfig(
          """init_strategy: "sync"
            |refresh_interval: -2
            |refresh_units: "minutes"
          """.stripMargin)
      }.refreshPeriodMs
    }

    an [IllegalArgumentException] should be thrownBy {
      new CommonsCacheConfiguration {
        override lazy val cacheConfig = YamlConfigFactory.loadConfig(
          """init_strategy: "sync"
            |refresh_interval: 2
            |refresh_units: "rubbish"
          """.stripMargin)
      }.refreshPeriodMs
    }

    val cacheConfigSecs = new CommonsCacheConfiguration {
      override lazy val cacheConfig = YamlConfigFactory.loadConfig(
        """init_strategy: "sync"
          |refresh_interval: 30
          |refresh_units: seconds
        """.stripMargin)
    }

    cacheConfigSecs.refreshPeriodMs shouldBe 30 * DateUtils.MILLIS_PER_SECOND

    val cacheConfigMins = new CommonsCacheConfiguration {
      override lazy val cacheConfig = YamlConfigFactory.loadConfig(
        """init_strategy: "sync"
          |refresh_interval: 5
          |refresh_units: minutes
        """.stripMargin)
    }

    cacheConfigMins.refreshPeriodMs shouldBe 5 * DateUtils.MILLIS_PER_MINUTE

    val cacheConfigHours = new CommonsCacheConfiguration {
      override lazy val cacheConfig = YamlConfigFactory.loadConfig(
        """init_strategy: "sync"
          |refresh_interval: 1
          |refresh_units: hours
        """.stripMargin)
    }

    cacheConfigHours.refreshPeriodMs shouldBe 1 * DateUtils.MILLIS_PER_HOUR
  }
}
