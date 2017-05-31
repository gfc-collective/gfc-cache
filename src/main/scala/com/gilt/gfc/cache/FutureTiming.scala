package com.gilt.gfc.cache

import com.gilt.gfc.time.Timer
import scala.concurrent.Future

trait FutureTiming {

  def timeFuture[T](report: String => Unit)(future: => Future[T]): Future[T] = {
    val start = System.nanoTime()
    val f = future
    f.onComplete(_ => report(Timer.pretty(System.nanoTime() - start)))
    f
  }

  def timeFuture[T](format: String, report: String => Unit)(future: => Future[T]): Future[T] = {
    timeFuture(r => report(format.format(r)))(future)
  }

}

