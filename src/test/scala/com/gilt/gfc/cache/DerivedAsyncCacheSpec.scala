package com.gilt.gfc.cache

import com.gilt.gfc.guava.cache.CacheInitializationStrategy
import com.google.common.collect.{HashBiMap, BiMap}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.concurrent.Eventually
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.reflectiveCalls
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class DerivedAsyncCacheSpec extends AnyFunSpec with Matchers with MockitoSugar with Eventually {
  import FutureHelpers._

  describe("An AsyncCacheEventNotifierImpl") {
    def createFixture = new {
      val testKey = "one"
      val testValue = 1

      val mockCache: Iterable[(String, Int)] = mock[Iterable[(String, Int)]]
      val testNotifier = new AsyncCacheEventNotifierImpl[String, Int] {}
      val mockHandler = mock[AsyncCacheEventHandler[String, Int]]
    }

    it("should notify registered handler for cache reload") {
      val f = createFixture
      f.testNotifier.registerHandler(f.mockHandler)
      f.testNotifier.notifyCacheReloadFor(f.mockCache)
      verify(f.mockHandler, times(1)).onCacheReload(f.mockCache)
    }

    it("should notify registered handler for cache miss reload") {
      val f = createFixture
      f.testNotifier.registerHandler(f.mockHandler)
      f.testNotifier.notifyCacheMissFor(f.testKey, f.testValue)
      verify(f.mockHandler, times(1)).onCacheMissLoad(f.testKey, f.testValue)
    }

    it("should skip the failing handlers and notify the rest") {
      val f = createFixture
      val failingHandler = mock[AsyncCacheEventHandler[String, Int]]
      when(failingHandler.onCacheReload(f.mockCache)).thenThrow(new RuntimeException("boom!"))
      when(failingHandler.onCacheMissLoad(f.testKey, f.testValue)).thenThrow(new RuntimeException("boom!"))

      f.testNotifier.registerHandler(failingHandler)
      f.testNotifier.registerHandler(f.mockHandler)

      f.testNotifier.notifyCacheReloadFor(f.mockCache)
      verify(f.mockHandler, times(1)).onCacheReload(f.mockCache)


      f.testNotifier.notifyCacheMissFor(f.testKey, f.testValue)
      verify(f.mockHandler, times(1)).onCacheMissLoad(f.testKey, f.testValue)

    }
  }

  describe("An async cache transformer") {
    def createFixture = new {
      val testValue = "one"
      val testKey = 1
      val testValue2 = "two"
      val testKey2 = 2
      val sourceObjects: BiMap[Int, String] = HashBiMap.create[Int, String]()

      sourceObjects.put(testKey, testValue)

      val testCache = new AsyncCacheImpl[Int, String] with CacheConfiguration {

        override def getSourceObject(key: Int): Future[Option[String]] = Future.successful(Option(sourceObjects.get(key)))

        override def refreshPeriodMs: Long = 1000L

        override def cacheInitStrategy: CacheInitializationStrategy = CacheInitializationStrategy.SYNC

        override def getSourceObjects: Future[Iterable[(Int, String)]] = Future.successful(sourceObjects.asScala)
      }

      val transformedKey = testValue
      val transformedValue = Future.successful(Some(testKey))
      val transformedKey2 = testValue2
      val transformedValue2 = Future.successful(Some(testKey2))
      val transformedTwiceValue2 = Future.successful(Some(testKey2.toString))

      val transformedCache: AsyncCache[String, Int] = new DerivedAsyncCacheImpl[Int, String, String, Int] {
        override def parent: AsyncCache[Int, String] = testCache
        override def transformSourceObject(k: Int, v: String): Iterable[(String, Int)] = Iterable[(String, Int)](v -> k)
        override def onCacheMiss(k: String): Future[Option[Int]] = Future.successful(Option(sourceObjects.inverse().get(k)))
      }

      val transformedTwiceKey = transformedKey
      val transformedTwiceValue = Future.successful(Some(testKey.toString))

      val transformedTwiceCache: AsyncCache[String, String] = new DerivedAsyncCacheImpl[String, Int, String, String] {
        override def parent: AsyncCache[String, Int] = transformedCache
        override def transformSourceObject(k: String, v: Int): Iterable[(String, String)] = Iterable(k -> v.toString)
        override def onCacheMiss(k: String): Future[Option[String]] = Future.successful(Option(sourceObjects.inverse().get(k)).map(_.toString))
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

      f.transformedCache.flatPeek(f.transformedKey) shouldBe None
      f.transformedCache.peek(f.transformedKey) shouldBe None

    }

    it("should not allow access to non registered derived cache") {
      val f = createFixture
      f.testCache.start()

      val transformedCache: AsyncCache[String, Int] = new DerivedAsyncCacheImpl[Int, String, String, Int] {
        override val parent: AsyncCache[Int, String] = f.testCache
        override def transformSourceObject(k: Int, v: String) = Iterable[(String, Int)](v -> k)
        override def onCacheMiss(k: String) = Future.successful(None)
      }

      transformedCache.isStarted shouldBe true
      transformedCache.asMap shouldBe Map.empty

      val ex = the[RuntimeException] thrownBy {
        transformedCache.get(f.transformedKey)
      }
      ex.getMessage shouldBe "You must explicitly register the derived cache before use. Please call .register() as part of attaching the cache to it's parent."
    }

    it("should bootstrap derived cache from parent") {
      val parentCache = createFixture.testCache

      parentCache.start()

      val derivedAsyncCache: DerivedAsyncCacheBase[Int, String, String, Int] = new DerivedAsyncCacheImpl[Int, String, String, Int] {
        override val parent: AsyncCache[Int, String] = parentCache
        override def transformSourceObject(k: Int, v: String) = Seq((v, k))
        override def onCacheMiss(k: String) = Future.successful(None)
      }

      derivedAsyncCache.register()
      derivedAsyncCache.isStarted shouldBe true

      derivedAsyncCache.asMap should not be Map.empty
    }

    it("should transform source object") {
      val f = createFixture

      f.testCache.start()

      val derivedAsyncCacheWATI = new DerivedAsyncCacheWithAsyncTransformImpl[Int, String, String, Int] {
        override val parent: AsyncCache[Int, String] = f.testCache
        override def transformSourceObjects(kvs: Iterable[(Int, String)]): Future[Iterable[(String, Int)]]
            = Future.successful(kvs.map{x:(Int, String) => (x._2, x._1)})
        override def onCacheMiss(k: String) = Future.successful(None)
      }

      derivedAsyncCacheWATI.register()

      f.sourceObjects.put(f.testKey2, f.testValue2)
      derivedAsyncCacheWATI.parent.get(f.testKey2)

      Thread.sleep(2000) 

      derivedAsyncCacheWATI.get(f.testValue2).await shouldBe Some(f.testKey2)
    }

    it("should transform started cache") {
      val f = createFixture
      f.testCache.start()

      f.transformedCache.isStarted shouldBe true
      f.transformedCache.asMap.keys should be(Set(f.transformedKey))

      f.transformedCache.get(f.transformedKey).await shouldBe f.transformedValue.await


      whenReady(f.transformedCache.get("nonExistingKey")) { (expectedResult: Option[Int]) =>
        expectedResult shouldBe None
      }

      whenReady(f.transformedValue) { (expectedResult: Some[Int]) =>

        f.transformedCache.peek(f.transformedKey).foreach { (result: Future[Option[Int]]) =>
          whenReady(result) { (readyResult: Option[Int]) =>
            readyResult shouldBe expectedResult
          }
        }

        f.transformedCache.flatPeek(f.transformedKey) shouldBe expectedResult
      }

    }

    it("should transform transformed cache") {
      val f = createFixture
      f.testCache.start()

      f.transformedTwiceCache.isStarted shouldBe true
      f.transformedTwiceCache.asMap.keys should be(Set(f.transformedTwiceKey))

      f.transformedTwiceCache.get(f.transformedTwiceKey).await shouldBe f.transformedTwiceValue.await


      whenReady(f.transformedTwiceCache.get("nonExistingKey")) { (expectedResult: Option[String]) =>
        expectedResult shouldBe None
      }

      whenReady(f.transformedTwiceValue) { (expectedResult: Some[String]) =>
        f.transformedTwiceCache.peek(f.transformedTwiceKey).foreach { (result: Future[Option[String]]) =>
          whenReady(result) { (readyResult: Option[String]) =>
            readyResult shouldBe expectedResult
          }
        }

        f.transformedTwiceCache.flatPeek(f.transformedTwiceKey) shouldBe expectedResult
      }

    }


    it("should reload when the parent cache is reloaded") {
      val f = createFixture
      f.testCache.start()

      f.testCache.get(f.testKey2).await shouldBe None
      f.transformedCache.get(f.transformedKey2).await shouldBe None
      f.transformedTwiceCache.get(f.transformedKey2).await shouldBe None


      f.sourceObjects.put(f.testKey2, f.testValue2)
      f.testCache.reload().await

      f.testCache.get(f.testKey2).await shouldBe Some(f.testValue2)
      f.transformedCache.get(f.transformedKey2).await shouldBe f.transformedValue2.await
      f.transformedTwiceCache.get(f.transformedKey2).await shouldBe f.transformedTwiceValue2.await
    }

    it("should update transformed cache on parent cache miss") {
      val f = createFixture
      f.testCache.start()

      f.testCache.peek(f.testKey2) shouldBe None
      f.transformedCache.peek(f.transformedKey2) shouldBe None
      f.transformedTwiceCache.peek(f.transformedKey2) shouldBe None

      f.sourceObjects.put(f.testKey2, f.testValue2)

      f.testCache.get(f.testKey2).await shouldBe Some(f.testValue2) //updates the child cache

      f.transformedCache.get(f.transformedKey2).await shouldBe f.transformedValue2.await
      f.transformedTwiceCache.get(f.transformedKey2).await shouldBe f.transformedTwiceValue2.await
    }

    it("should update transformed cache on child cache miss") {
      val f = createFixture
      f.testCache.start()

      f.testCache.peek(f.testKey2) shouldBe None
      f.transformedCache.peek(f.transformedKey2) shouldBe None
      f.transformedTwiceCache.peek(f.transformedKey2) shouldBe None

      f.sourceObjects.put(f.testKey2, f.testValue2)

      f.transformedCache.get(f.transformedKey2).await shouldBe f.transformedValue2.await
      eventually(f.transformedTwiceCache.peek(f.transformedKey2).get.await shouldBe f.transformedTwiceValue2.await)
      f.testCache.peek(f.testKey2) shouldBe None //parent cache intact
    }

  }


}
