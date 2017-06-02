package com.gilt.gfc.cache

import java.util.concurrent.ConcurrentHashMap

import com.gilt.gfc.guava.cache.CacheInitializationStrategy
import com.google.common.base.Optional
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.reflectiveCalls


class AsyncToSyncCacheAdapterSpec extends FunSpec with Matchers with MockitoSugar with Eventually {
  import FutureHelpers._

  describe("A sync cache view") {
    def createFixture = new {
      val testValue = "one"
      val testKey = 1
      val testValue2 = "two"
      val testKey2 = 2
      val testValue3 = "three"
      val testKey3 = 3
      val sourceObjects = new ConcurrentHashMap[Int, String]().asScala
      sourceObjects.put(testKey, testValue)

      val testCache = new AsyncCacheImpl[Int, String]  with CacheConfiguration {
        override def refreshPeriodMs: Long = 1000L
        override def cacheInitStrategy: CacheInitializationStrategy = CacheInitializationStrategy.SYNC
        override def getSourceObjects: Future[Iterable[(Int, String)]] = Future.successful(sourceObjects)
        override def getSourceObject(key: Int): Future[Option[String]] = Future.successful(if (key==testKey3) Some(testValue3) else None)
      }

      val syncView = new AsyncToSyncCacheAdapter[Int, String] {
        override val parent = testCache
      }.register()
    }


    it("should transform non started cache into an empty cache") {
      val f = createFixture
      f.syncView.isStarted shouldBe false
      f.syncView.asMap shouldBe Map.empty
      (the[RuntimeException] thrownBy f.syncView.get(f.testKey)).getMessage shouldBe
        "You must explicitly start the cache before use. Please call .start() as part of your application's startup sequence."

    }

    it("should transform started cache") {
      val f = createFixture
      f.testCache.start()

      f.syncView.isStarted shouldBe true
      f.syncView.asMap should be(Map(f.testKey -> f.testValue))
      f.syncView.get(f.testKey) shouldBe Some(f.testValue)
      f.syncView.getOpt(f.testKey) shouldBe Optional.of(f.testValue)

      f.syncView.get(f.testKey2) shouldBe None
      f.syncView.getOpt(f.testKey2) shouldBe Optional.absent()
    }

    it("should reload when the parent cache is reloaded") {
      val f = createFixture
      f.testCache.start()

      f.sourceObjects.put(f.testKey2, f.testValue2)
      f.testCache.reload().await

      f.syncView.asMap should be(Map(f.testKey -> f.testValue, f.testKey2 -> f.testValue2))
    }

    it("should pick up a cache miss on the parent") {
      val f = createFixture
      f.testCache.start()

      f.syncView.asMap should be(Map(f.testKey -> f.testValue))

      f.testCache.get(f.testKey3)
      eventually(f.syncView.get(f.testKey3) shouldBe Some(f.testValue3))

      f.syncView.asMap should be(Map(f.testKey -> f.testValue, f.testKey3 -> f.testValue3))
    }

    it("should trigger a cache miss on the parent") {
      val f = createFixture
      f.testCache.start()

      f.syncView.asMap should be(Map(f.testKey -> f.testValue))

      f.syncView.get(f.testKey3) shouldBe None
      eventually(f.syncView.get(f.testKey3) shouldBe Some(f.testValue3))

      f.syncView.asMap should be(Map(f.testKey -> f.testValue, f.testKey3 -> f.testValue3))
    }
  }
}
