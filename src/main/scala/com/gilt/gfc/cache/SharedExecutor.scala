package com.gilt.gfc.cache

import java.util.concurrent.Executors
import com.gilt.gfc.concurrent.{AsyncScheduledExecutorService, ExecutorService, ScheduledExecutorService}
import com.gilt.gfc.concurrent.JavaConverters._
import com.gilt.gfc.concurrent.{ThreadGroupBuilder, ThreadFactoryBuilder}
import org.slf4j.LoggerFactory

/**
 * A configurable executor which commons may use.  Users may specify how big it is, or it will pick a sensible default.
 *
 * @author Eric Bowman
 * @since 10/4/11 9:38 AM
 */
object SharedExecutor extends SharedExecutor {
  val NumOfCores: Int = Runtime.getRuntime.availableProcessors

  private val DefaultThreadCount = NumOfCores
  private val Log = LoggerFactory.getLogger(getClass)
}

trait SharedExecutor {
  import SharedExecutor._

  lazy val executor: ExecutorService = {
    val threadCount = DefaultThreadCount
    Log.info("Starting shared executor with {} threads", threadCount)
    Executors.unconfigurableExecutorService(
      Executors.newFixedThreadPool(threadCount, newThreadFactory("SharedExecutor.executor"))
    ).asScala
  }

  lazy val scheduledExecutor: AsyncScheduledExecutorService = {
    val threadCount =  DefaultThreadCount
    Log.info("Starting shared scheduled executor with {} threads", threadCount)
    Executors.unconfigurableScheduledExecutorService(
      Executors.newScheduledThreadPool(threadCount, newThreadFactory("SharedExecutor.scheduledExecutor"))
    ).asScala
  }

  lazy val singleThreadScheduledExecutor: ScheduledExecutorService = {
    Executors.newSingleThreadScheduledExecutor(newThreadFactory("SharedExecutor.singleThreadScheduledExecutor")).asScala
  }

  private def newThreadFactory(name: String) = {
    val group = ThreadGroupBuilder().withName(name).withDaemonFlag(false).build()
    ThreadFactoryBuilder().
      withNameFormat(name + "-%d").
      withThreadGroup(group).
      withDaemonFlag(true).
      withUncaughtExceptionHandler(ThreadFactoryBuilder.LogUncaughtExceptionHandler).
      build()
  }
}
