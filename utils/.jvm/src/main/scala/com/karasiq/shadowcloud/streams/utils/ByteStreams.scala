package com.karasiq.shadowcloud.streams.utils

import java.io.IOException

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.utils.MemorySize

object ByteStreams {
  type ByteFlow = Flow[ByteString, ByteString, NotUsed]

  def limit(limit: Long): ByteFlow = {
    Flow.fromGraph(new ByteStringLimit(limit, truncate = false))
  }

  def truncate(limit: Long): ByteFlow = {
    Flow.fromGraph(new ByteStringLimit(limit, truncate = true))
  }

  def concat: ByteFlow = {
    Flow.fromGraph(new ByteStringConcat)
  }

  private final class ByteStringConcat extends GraphStage[FlowShape[ByteString, ByteString]] {
    val inlet = Inlet[ByteString]("ByteStringConcat.in")
    val outlet = Outlet[ByteString]("ByteStringConcat.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val builder = ByteString.newBuilder
      builder.sizeHint(256)

      def onPull(): Unit = {
        if (isClosed(inlet)) {
          push(outlet, builder.result())
          completeStage()
        } else {
          pull(inlet)
        }
      }

      def onPush(): Unit = {
        builder.append(grab(inlet))
        pull(inlet)
      }

      override def onUpstreamFinish(): Unit = {
        if (isAvailable(outlet)) {
          push(outlet, builder.result())
          completeStage()
        }
      }

      setHandlers(inlet, outlet, this)
    }
  }

  private final class ByteStringLimit(limit: Long, truncate: Boolean) extends GraphStage[FlowShape[ByteString, ByteString]] {
    val inlet = Inlet[ByteString]("ByteStringConcat.in")
    val outlet = Outlet[ByteString]("ByteStringConcat.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] var written = 0L

      private[this] def isLimitReached(size: Long): Boolean = {
        size > limit
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
          if (isLimitReached(written + 1)) completeStage()
        } else if (isLimitReached(written + bytes.length)) {
          failStage(new IOException(s"Write limit reached: ${MemorySize.toString(limit)}"))
        } else {
          written += bytes.length
          push(outlet, bytes)
        }
      }


      setHandlers(inlet, outlet, this)
    }
  }
}
