package com.karasiq.shadowcloud.utils

import scala.concurrent.duration.{Duration, FiniteDuration}

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

object AkkaStreamUtils {
  def groupedOrInstant[T, M](queueSize: Int, queueTime: FiniteDuration): Flow[T, Seq[T], NotUsed] = {
    if (queueSize == 0 || queueTime == Duration.Zero) {
      Flow[T].map(Seq(_: T))
    } else {
      Flow[T].groupedWithin(queueSize, queueTime)
    }
  }

  def extractStream[T]: Flow[T, Source[T, NotUsed], NotUsed] = {
    Flow[T].prefixAndTail(0).map(_._2)
  }
}
