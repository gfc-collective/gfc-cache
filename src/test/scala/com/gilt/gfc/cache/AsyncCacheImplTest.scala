package com.gilt.gfc.cache

import java.util.concurrent.{CountDownLatch, CyclicBarrier}
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import com.gilt.gfc.guava.cache.CacheInitializationStrategy
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.ArgumentMatchers._
import com.google.common.base.Optional
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers


class AsyncCacheImplTest extends AnyFunSpec with Matchers with MockitoSugar {
  import scala.language.reflectiveCalls

  trait Fixture[K, V] {
    def getSourceObject(key: K): Option[V]
    def getSourceObjects: Iterable[(K, V)]
  }

  class TestCache[K, V](f: Fixture[K, V]) extends AsyncCacheImpl[K, V] with CacheConfiguration {
    override def getSourceObject(key: K): Future[Option[V]] = Future.successful(f.getSourceObject(key))
    override def getSourceObjects: Future[Iterable[(K, V)]] = Future.successful(f.getSourceObjects)
    override def refreshPeriodMs: Long = 1000000L
    override def cacheInitStrategy: CacheInitializationStrategy = CacheInitializationStrategy.SYNC
    override def reload() = super.reload()
  }

  it("bulk load value") {
    val f = mock[Fixture[Int, String]]
    when(f.getSourceObjects).thenReturn(Iterable(1 -> "one"))
    when(f.getSourceObject(any())).thenReturn(None)

    val cache = new TestCache(f).start()
    cache.jGet(1).get shouldBe Optional.of("one")

    verify(f).getSourceObjects
    verifyNoMoreInteractions(f)
  }

  it("value missing on cache miss") {
    val f = mock[Fixture[Int, String]]
    when(f.getSourceObjects).thenReturn(Iterable(1 -> "one"))
    when(f.getSourceObject(any())).thenReturn(None)

    val cache = new TestCache(f).start()
    cache.jGet(2).get shouldBe Optional.absent()

    verify(f).getSourceObjects
    verify(f).getSourceObject(2)
    verifyNoMoreInteractions(f)
  }

  it("repreated call does not trigger lookup") {
    val f = mock[Fixture[Int, String]]
    when(f.getSourceObjects).thenReturn(Iterable(1 -> "one"))
    when(f.getSourceObject(any())).thenReturn(None)

    val cache = new TestCache(f).start()
    (0 to 3).foreach(_ => cache.jGet(2).get shouldBe Optional.absent())

    verify(f).getSourceObjects
    verify(f).getSourceObject(2)
    verifyNoMoreInteractions(f)
  }

  it("load value on cache miss") {
    val f = mock[Fixture[Int, String]]
    when(f.getSourceObjects).thenReturn(Iterable(1 -> "one"))
    when(f.getSourceObject(any())).thenReturn(None)
    when(f.getSourceObject(2)).thenReturn(Some("two"))

    val cache = new TestCache(f).start()
    cache.jGet(2).get shouldBe Optional.of("two")

    verify(f).getSourceObjects
    verify(f).getSourceObject(2)
    verifyNoMoreInteractions(f)
  }

  it("not load value on peek") {
    val f = mock[Fixture[Int, String]]
    when(f.getSourceObjects).thenReturn(Iterable(1 -> "one"))
    when(f.getSourceObject(any())).thenReturn(None)
    when(f.getSourceObject(2)).thenReturn(Some("two"))

    val cache = new TestCache(f).start()
    cache.peek(1) should be ('defined)
    cache.peek(2) should be ('empty)
    cache.peek(3) should be ('empty)

    verify(f).getSourceObjects
    verifyNoMoreInteractions(f)
  }

  it("still not load value on peek") {
    val f = mock[Fixture[Int, String]]
    when(f.getSourceObjects).thenReturn(Iterable(1 -> "one"))
    when(f.getSourceObject(any())).thenReturn(None)
    when(f.getSourceObject(2)).thenReturn(Some("two"))

    val cache = new TestCache(f).start()
    cache.peek(2) should be ('empty)
    cache.jGet(2).get shouldBe Optional.of("two")
    cache.peek(2) should be ('defined)

    cache.peek(3) should be ('empty)
    cache.jGet(3).get shouldBe Optional.absent()
    cache.peek(3) should be ('defined)

    verify(f).getSourceObjects
    verify(f).getSourceObject(2)
    verify(f).getSourceObject(3)
    verifyNoMoreInteractions(f)
  }

  it("reloads") {
    val f = mock[Fixture[Int, String]]
    when(f.getSourceObjects).thenReturn(Iterable(1 -> "one"))
    when(f.getSourceObject(any())).thenReturn(None)

    val cache = new TestCache(f).start()
    cache.reload()

    verify(f, times(2)).getSourceObjects
    verifyNoMoreInteractions(f)
  }

  it("makes only one remote call on cache miss") {

    val f = new Fixture[Int, Int] {
      val counter = new AtomicInteger()

      override def getSourceObject(key: Int) = {
        Some(counter.incrementAndGet)
      }

      override def getSourceObjects = Iterable.empty
    }

    val cache = new TestCache(f).start()

    val numThreads = 25
    val numTries = 1000

    val barrier = new CyclicBarrier(numThreads)
    val latch = new CountDownLatch(numThreads)

    val threads = (0 until numThreads).map { _ =>
      val t = new Thread(new Runnable {
        override def run(): Unit = {
          (0 until numTries).map { i =>
            barrier.await()
            val res = cache.jGet(i).get
            if (res != Optional.of(i+1)) {
              println(s"!!!!!!!! res=$res i=$i")
            }
          }

          // thread finished
          latch.countDown()
        }
      })
      t.start()
      t
    }

    // wait for threads to finish
    latch.await()

    f.counter.get shouldBe (numTries)
  }
}
