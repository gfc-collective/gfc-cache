package org.gfccollective.cache

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * Little helpers for scala futures, only to be used in tests!
  *
  * @author Gregor Heine
  * @since 11/Jul/2014 13:25
  */
object FutureHelpers {
  implicit class AwaitableFuture[A](val f: Future[A]) extends AnyVal {
    @inline def await: A = Await.result(f, Duration.Inf)
  }

  implicit class AsFuture[A](val a: A) extends AnyVal {
    @inline def asFuture: Future[A] = Future.successful(a)
  }
}
