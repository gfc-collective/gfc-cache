package org.gfccollective.cache

import java.util.concurrent.ConcurrentHashMap

import org.gfccollective.guava.cache.CacheInitializationStrategy
import com.google.common.base.Optional
import org.mockito.Mockito._
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.reflectiveCalls
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers


class DerivedSyncCacheSpec extends AnyFunSpec with Matchers with MockitoSugar {
  import FutureHelpers._

  describe("A SyncCacheEventNotifierImpl") {
    def createFixture = new {
      val mockCache: Iterable[(String, Int)] = mock[Iterable[(String, Int)]]
      val testNotifier = new SyncCacheEventNotifierImpl[String,Int]{}
      val mockHandler = mock[SyncCacheEventHandler[String,Int]]
    }



    it("should notify registered handler for cache reload"){
      val f = createFixture
      f.testNotifier.registerHandler(f.mockHandler)
      f.testNotifier.notifyCacheReloadFor(f.mockCache)
      verify(f.mockHandler, times(1)).onCacheReload(f.mockCache)
    }

    it("should skip the failing handlers and notify the rest"){
      val f = createFixture
      val failingHandler = mock[SyncCacheEventHandler[String,Int]]
      when(failingHandler.onCacheReload(f.mockCache)).thenThrow(new RuntimeException("boom!"))

      f.testNotifier.registerHandler(failingHandler)
      f.testNotifier.registerHandler(f.mockHandler)

      f.testNotifier.notifyCacheReloadFor(f.mockCache)

      verify(f.mockHandler, times(1)).onCacheReload(f.mockCache)
    }
  }


  describe("A TransformerCacheBaseImpl") {

    def createFixture = new {
      val mockParent = mock[CacheBase]
      val transformerCacheBaseImpl = new DerivedCacheBaseImpl {
        override def parent: CacheBase = mockParent
      }
    }

    it("should start the parent cache first before it start itself") {
      val f = createFixture

      f.transformerCacheBaseImpl.isStarted shouldBe false
      f.mockParent.isStarted shouldBe false

      f.transformerCacheBaseImpl.start()

      verify(f.mockParent,  times(1)).start()
    }

    it("should be started when the parent cache is started") {
      val f = createFixture

      when(f.mockParent.isStarted).thenReturn(true)
      f.transformerCacheBaseImpl.isStarted shouldBe true
    }

    it("should not shutdown the parent cache when it's been shutdown") {
      val f = createFixture

      f.transformerCacheBaseImpl.shutdown()
      verify(f.mockParent,  times(0)).shutdown()
    }

  }

  describe("test DerivedSyncCacheImpl"){
    def createFixture = new {
      val testValue = "one"
      val testKey = 1
      val testValue2 = "two"
      val testKey2 = 2
      val sourceObjects = new ConcurrentHashMap[Int, String]().asScala
      sourceObjects.put(testKey, testValue)

      val testCache = new SyncCacheImpl[Int, String]  with CacheConfiguration {
        override def refreshPeriodMs: Long = 1000L
        override def cacheInitStrategy: CacheInitializationStrategy = CacheInitializationStrategy.SYNC
        override def getSourceObjects: Future[Iterable[(Int, String)]] = Future.successful(sourceObjects)
      }

      val transformedKey = testValue
      val transformedValue = testKey
      val transformedKey2 = testValue2
      val transformedValue2 = testKey2

      val transformedCache: SyncCache[String, Int] = new DerivedSyncCacheImpl[Int, String, String, Int] {
        override def parent: SyncCache[Int, String] = testCache
        override def transformSourceObject(k: Int, v: String): Iterable[(String, Int)] = Iterable[(String, Int)](v -> k)
      }

      val transformedTwiceCache: SyncCache[String, String] = new DerivedSyncCacheImpl[String, Int, String, String] {
        override def parent: SyncCache[String, Int] = transformedCache
        override def transformSourceObject(k: String, v: Int): Iterable[(String, String)] = Iterable(k -> v.toString)
      }
    }

    it("should not allow access to non started parent cache") {
      val f = createFixture

      f.transformedCache.isStarted shouldBe false
      f.transformedCache.asMap shouldBe Map.empty

      val ex = the[RuntimeException] thrownBy {
        f.transformedCache.get(f.transformedKey)
      }
      ex.getMessage shouldBe "You must explicitly start the cache before use. Please call .start() as part of your application's startup sequence."
    }

    it("should not allow access to non registered derived cache") {
      val f = createFixture
      f.testCache.start()

      val transformedCache: SyncCache[String, Int] = new DerivedSyncCacheImpl[Int, String, String, Int] {
        override val parent: SyncCache[Int, String] = f.testCache
        override def transformSourceObject(k: Int, v: String): Iterable[(String, Int)] = Iterable[(String, Int)](v -> k)
      }

      transformedCache.isStarted shouldBe true
      transformedCache.asMap shouldBe Map.empty

      val ex = the[RuntimeException] thrownBy {
        transformedCache.get(f.transformedKey)
      }
      ex.getMessage shouldBe "You must explicitly register the derived cache before use. Please call .register() as part of attaching the cache to it's parent."
    }

    it("should transform cache on start") {
      val f = createFixture
      f.testCache.start()

      f.transformedCache.isStarted shouldBe true
      f.transformedCache.asMap should be(Map(f.transformedKey -> f.transformedValue))
      f.transformedCache.get(f.transformedKey) shouldBe Some(f.transformedValue)
      f.transformedCache.getOpt(f.transformedKey) shouldBe Optional.of(f.transformedValue)

      f.transformedCache.get("nonExistingKey") shouldBe None
      f.transformedCache.getOpt("nonExistingKey") shouldBe Optional.absent()
    }

    it("should transform already started cache") {
      val f = createFixture
      f.testCache.start()

      val transformedCache: SyncCache[String, Int] = new DerivedSyncCacheImpl[Int, String, String, Int] {
        override def parent: SyncCache[Int, String] = f.testCache
        override def transformSourceObject(k: Int, v: String): Iterable[(String, Int)] = Iterable[(String, Int)](v -> k)
      }

      transformedCache.isStarted shouldBe true
      transformedCache.asMap should be(Map(f.transformedKey -> f.transformedValue))
      transformedCache.get(f.transformedKey) shouldBe Some(f.transformedValue)
      transformedCache.getOpt(f.transformedKey) shouldBe Optional.of(f.transformedValue)

      transformedCache.get("nonExistingKey") shouldBe None
      transformedCache.getOpt("nonExistingKey") shouldBe Optional.absent()
    }

    it("should transform transformed cache") {
      val f = createFixture
      f.testCache.start()

      f.transformedTwiceCache.isStarted shouldBe true

      f.transformedTwiceCache.asMap should be(Map(f.transformedKey -> f.transformedValue.toString))
      f.transformedTwiceCache.get(f.transformedKey) shouldBe Some(f.transformedValue.toString)
      f.transformedTwiceCache.getOpt(f.transformedKey) shouldBe Optional.of(f.transformedValue.toString)

      f.transformedTwiceCache.get("nonExistingKey") shouldBe None
      f.transformedTwiceCache.getOpt("nonExistingKey") shouldBe Optional.absent()
    }

    it("should reload when the parent cache is reloaded") {
      val f = createFixture
      f.testCache.start()

      f.sourceObjects.put(f.testKey2, f.testValue2)
      f.testCache.reload().await

      f.transformedCache.asMap should be(Map(f.transformedKey -> f.transformedValue, f.transformedKey2 -> f.transformedValue2))
      f.transformedTwiceCache.asMap should be(Map(f.transformedKey -> f.transformedValue.toString, f.transformedKey2 -> f.transformedValue2.toString))
    }
  }

  describe("test ValueDerivedSyncCacheImpl"){
    def createFixture = new {
      val testValue = "one"
      val testKey = 1
      val testValue2 = "two"
      val testKey2 = 2
      val sourceObjects = new ConcurrentHashMap[Int, String]().asScala
      sourceObjects.put(testKey, testValue)

      val testCache = new SyncCacheImpl[Int, String]  with CacheConfiguration {
        override def refreshPeriodMs: Long = 1000L
        override def cacheInitStrategy: CacheInitializationStrategy = CacheInitializationStrategy.SYNC
        override def getSourceObjects: Future[Iterable[(Int, String)]] = Future.successful(sourceObjects)
      }

      val transformedValue = testValue.length
      val transformedValue2 = testValue2.length

      val transformedCache: SyncCache[Int, Int] = new ValueDerivedSyncCacheImpl[Int, String, Int] {
        override def parent: SyncCache[Int, String] = testCache
        override def transformValue(k: Int, v: String): Option[Int] = Some(v.length)
      }

      val transformedTwiceCache: SyncCache[Int, String] = new ValueDerivedSyncCacheImpl[Int, Int, String] {
        override def parent: SyncCache[Int, Int] = transformedCache
        override def transformValue(k: Int, v: Int): Option[String] = Some(v.toString)
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

      val transformedCache: SyncCache[Int, Int] = new ValueDerivedSyncCacheImpl[Int, String, Int] {
        override val parent: SyncCache[Int, String] = f.testCache
        override def transformValue(k: Int, v: String): Option[Int] = Some(v.length)
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
      f.transformedCache.asMap should be(Map(f.testKey -> f.transformedValue))
      f.transformedCache.get(f.testKey) shouldBe Some(f.transformedValue)
      f.transformedCache.getOpt(f.testKey) shouldBe Optional.of(f.transformedValue)

      f.transformedCache.get(3) shouldBe None
      f.transformedCache.getOpt(3) shouldBe Optional.absent()
    }

    it("should transform already started cache") {
      val f = createFixture
      f.testCache.start()

      val transformedCache: SyncCache[Int, Int] = new ValueDerivedSyncCacheImpl[Int, String, Int] {
        override def parent: SyncCache[Int, String] = f.testCache
        override def transformValue(k: Int, v: String): Option[Int] = Some(v.length)
      }

      transformedCache.isStarted shouldBe true
      transformedCache.asMap should be(Map(f.testKey -> f.transformedValue))
      transformedCache.get(f.testKey) shouldBe Some(f.transformedValue)
      transformedCache.getOpt(f.testKey) shouldBe Optional.of(f.transformedValue)

      transformedCache.get(3) shouldBe None
      transformedCache.getOpt(3) shouldBe Optional.absent()
    }

    it("should transform transformed cache") {
      val f = createFixture
      f.testCache.start()

      f.transformedTwiceCache.isStarted shouldBe true

      f.transformedTwiceCache.asMap should be(Map(f.testKey -> f.transformedValue.toString))
      f.transformedTwiceCache.get(f.testKey) shouldBe Some(f.transformedValue.toString)
      f.transformedTwiceCache.getOpt(f.testKey) shouldBe Optional.of(f.transformedValue.toString)

      f.transformedTwiceCache.get(3) shouldBe None
      f.transformedTwiceCache.getOpt(3) shouldBe Optional.absent()
    }

    it("should reload when the parent cache is reloaded") {
      val f = createFixture
      f.testCache.start()

      f.sourceObjects.put(f.testKey2, f.testValue2)
      f.testCache.reload().await

      f.transformedCache.asMap should be(Map(f.testKey -> f.transformedValue, f.testKey2 -> f.transformedValue2))
      f.transformedTwiceCache.asMap should be(Map(f.testKey -> f.transformedValue.toString, f.testKey2 -> f.transformedValue2.toString))
    }
  }
}
