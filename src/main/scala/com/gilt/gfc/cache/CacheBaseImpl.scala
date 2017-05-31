package com.gilt.gfc.cache

import java.util.concurrent.{ScheduledFuture, TimeUnit}

import com.gilt.gfc.guava.cache.CacheInitializationStrategy
import com.gilt.gfc.logging.Loggable
import com.gilt.gfc.time.Timer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Cache Base Implementation that requires the implementation of a function to partially re-load the cached data
 * (e.g. by issuing a remote call). This function is periodically called to refresh the cache.
 *
 * @author Gregor Heine
 * @since 30/Jul/2014 14:07
 */
private[cache] object CacheBaseImpl {
  val defaultExecutor = SharedExecutor.scheduledExecutor
  val defaultContext = ExecutionContext.fromExecutor(defaultExecutor)
}

private[cache] trait CacheBaseImpl[K, V] extends CacheBase with FutureTiming with Loggable {
  self: CacheConfiguration =>

  @volatile
  private var future: Option[ScheduledFuture[_]] = None

  /**
   * The source data that will be used to build all views
   * @return an iterator of key-value pairs that will be used to build all cached views
   */
  def getSourceObjects: Future[Iterable[(K, V)]]

  protected def buildCache(kvs: Iterable[(K, V)]): Unit

  protected def executor = CacheBaseImpl.defaultExecutor

  override def isStarted = future.isDefined

  private val startMutex = new Object

  override def start(): this.type = {
    startMutex.synchronized {
      future = future.orElse {

        val initialDelayMs = cacheInitStrategy match {
          case CacheInitializationStrategy.ASYNC =>
            0
          case CacheInitializationStrategy.SYNC =>
            import scala.concurrent.Await
            import scala.concurrent.duration.Duration
            Await.result(reload(), Duration.Inf)
            refreshPeriodMs
        }

        Some(executor.asyncScheduleWithFixedDelay(FiniteDuration(initialDelayMs, TimeUnit.MILLISECONDS), FiniteDuration(refreshPeriodMs, TimeUnit.MILLISECONDS))(reload()))
      }
    }
    this
  }

  override def shutdown(): Unit = future.foreach(_.cancel(true))

  def reload(): Future[Unit] = {
    implicit val context = CacheBaseImpl.defaultContext
    info("Starting cache reload...")
    val start = System.nanoTime()
    val f = getSourceObjects.map(buildCache)
    f.onComplete {
      case Success(_) => info(s"Cache reload succeeded after ${Timer.pretty(System.nanoTime() - start)}")
      case Failure(t) => error(s"Cache reload failed after ${Timer.pretty(System.nanoTime() - start)}: ${t.getMessage}", t)
    }
    f
  }
}
