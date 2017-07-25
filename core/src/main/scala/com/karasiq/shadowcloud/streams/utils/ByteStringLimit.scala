package com.karasiq.shadowcloud.streams.utils

import java.io.IOException

import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.utils.MemorySize

object ByteStringLimit {
  def apply(limit: Long, truncate: Boolean = true): Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new ByteStringLimit(limit, truncate))
  }
}

private final class ByteStringLimit(limit: Long, truncate: Boolean) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val inlet = Inlet[ByteString]("ByteStringConcat.in")
  val outlet = Outlet[ByteString]("ByteStringConcat.out")
  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] var written = 0L

    private[this] def isLimitReached(size: Long): Boolean = {
      size >= limit
    }

    def onPull(): Unit = {
      tryPull(inlet)
    }

    def onPush(): Unit = {
      val bytes = grab(inlet)
      if (truncate) {
        val size = math.max(0L, math.min(limit - written, bytes.length)).toInt
        val truncated = bytes.take(size)
        written += size
        push(outlet, truncated)
      } else if (!isLimitReached(written + bytes.length)) {
        written += bytes.length
        push(outlet, bytes)
      }

      if (isLimitReached(written)) {
        failStage(new IOException(s"Write limit reached: ${MemorySize.toString(limit)}"))
      }
    }


    setHandlers(inlet, outlet, this)
  }
}