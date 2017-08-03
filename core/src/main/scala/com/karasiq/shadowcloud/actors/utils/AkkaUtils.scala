package com.karasiq.shadowcloud.actors.utils

import scala.concurrent.duration.{Duration, FiniteDuration}

import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl.Source

private[actors] object AkkaUtils {
  implicit class SourceOps[T, M](private val sourceGraph: Graph[SourceShape[T], M]) extends AnyVal {
    @inline
    private[this] def source: Source[T, M] = Source.fromGraph(sourceGraph)

    def groupedOrInstant(queueSize: Int, queueTime: FiniteDuration): Source[Seq[T], M] = {
      if (queueSize == 0 || queueTime == Duration.Zero) {
        source.map(Seq(_: T))
      } else {
        source.groupedWithin(queueSize, queueTime)
      }
    }
  }
}
