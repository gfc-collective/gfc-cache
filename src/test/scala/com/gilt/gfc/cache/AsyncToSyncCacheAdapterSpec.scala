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
  import scala.concurrent.duration._

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(1 second))

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

  describe("test AsyncToSyncCacheAdapter") {
    def createFixture = new {
      val testValue = "one"
      val testKey = 1
      val testValue2 = "two"
      val testKey2 = 2
      val sourceObjects = new ConcurrentHashMap[Int, String]().asScala
      sourceObjects.put(testKey, testValue)

      val testCache  = new AsyncCacheImpl[Int, String] with CacheConfiguration {
        override def getSourceObject(key: Int): Future[Option[String]] = Future.successful(sourceObjects.get(key))
        override def refreshPeriodMs: Long = 1000L
        override def cacheInitStrategy: CacheInitializationStrategy = CacheInitializationStrategy.SYNC
        override def getSourceObjects: Future[Iterable[(Int, String)]] = Future.successful(sourceObjects.toList)
      }

      val transformedCache = new AsyncToSyncCacheAdapter[Int, String] {
        override def parent: AsyncCache[Int, String] = testCache
      }
    }

    it("should not allow access to non started parent cache") {
      val f = createFixture

      f.transformedCache.isStarted shouldBe false
      f.transformedCache.asMap shouldBe Map.empty

      val ex = the[RuntimeException] thrownBy {
        f.transformedCache.get(f.testKey)
      }
      ex.getMessage shouldBe "You must explicitly start the cache before use. Please call .start() as part of your application's startup sequence."
    }

    it("should not allow access to non registered derived cache") {
      val f = createFixture
      f.testCache.start()

      val transformedCache: SyncCache[Int, String] = new AsyncToSyncCacheAdapter[Int, String] {
        override val parent: AsyncCache[Int, String] = f.testCache
      }

      transformedCache.isStarted shouldBe true
      transformedCache.asMap shouldBe Map.empty

      val ex = the[RuntimeException] thrownBy {
        transformedCache.get(f.testKey)
      }
      ex.getMessage shouldBe "You must explicitly register the derived cache before use. Please call .register() as part of attaching the cache to it's parent."
    }

    it("should transform cache on start") {
      val f = createFixture
      f.testCache.start()

      f.transformedCache.isStarted shouldBe true
      f.transformedCache.asMap should be(Map(f.testKey -> f.testValue))
      f.transformedCache.get(f.testKey) shouldBe Some(f.testValue)
      f.transformedCache.getOpt(f.testKey) shouldBe Optional.of(f.testValue)

      f.transformedCache.get(3) shouldBe None
      f.transformedCache.getOpt(3) shouldBe Optional.absent()
    }

    it("should transform already started cache") {
      val f = createFixture
      f.testCache.start()

      val transformedCache: SyncCache[Int, String] = new AsyncToSyncCacheAdapter[Int, String] {
        override def parent: AsyncCache[Int, String] = f.testCache
      }

      transformedCache.isStarted shouldBe true
      transformedCache.asMap should be(Map(f.testKey -> f.testValue))
      transformedCache.get(f.testKey) shouldBe Some(f.testValue)
      transformedCache.getOpt(f.testKey) shouldBe Optional.of(f.testValue)

      transformedCache.get(3) shouldBe None
      transformedCache.getOpt(3) shouldBe Optional.absent()
    }

    it("should reload when the parent cache is reloaded") {
      val f = createFixture
      f.testCache.start()

      f.sourceObjects.put(f.testKey2, f.testValue2)
      f.testCache.reload().await

      f.transformedCache.asMap should be(Map(f.testKey -> f.testValue, f.testKey2 -> f.testValue2))
    }

    it("should propagate parent cache loads to child cache") {
      val f = createFixture
      f.testCache.start()

      f.transformedCache.get(f.testKey2) shouldBe None
      f.testCache.get(f.testKey2).await shouldBe None

      f.testCache.reload().await
      f.sourceObjects.put(f.testKey2, f.testValue2)

      f.transformedCache.get(f.testKey2) shouldBe None
      f.testCache.get(f.testKey2).await shouldBe Some(f.testValue2)
      eventually(f.transformedCache.get(f.testKey2) shouldBe Some(f.testValue2))
    }

    it("should only replace cache entry when it previously had a miss") {
      val f = createFixture
      f.testCache.start()

      f.transformedCache.get(f.testKey2) shouldBe None
      f.transformedCache.onCacheMissLoad(f.testKey2, f.testValue2)
      f.transformedCache.get(f.testKey2) shouldBe Some(f.testValue2)
      f.transformedCache.onCacheMissLoad(f.testKey2, "invalid")
      f.transformedCache.get(f.testKey2) shouldBe Some(f.testValue2)
    }
  }
}
