package com.karasiq.shadowcloud.streams.utils

import java.io.IOException

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.karasiq.common.memory.{MemorySize, SizeUnit}

import scala.collection.mutable.ListBuffer

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

  def bufferT[T](getSize: T ⇒ Long, maxSize: Long): Flow[T, T, NotUsed] = {
    Flow.fromGraph(new BytesBuffer[T](getSize, maxSize))
  }

  def buffer(maxSize: Long): ByteFlow =
    bufferT[ByteString](_.length, maxSize)

  private[this] final class ByteStringConcat extends GraphStage[FlowShape[ByteString, ByteString]] {
    val inlet  = Inlet[ByteString]("ByteStringConcat.in")
    val outlet = Outlet[ByteString]("ByteStringConcat.out")
    val shape  = FlowShape(inlet, outlet)

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

  private[this] final class ByteStringLimit(limit: Long, truncate: Boolean) extends GraphStage[FlowShape[ByteString, ByteString]] {
    val inlet  = Inlet[ByteString]("ByteStringConcat.in")
    val outlet = Outlet[ByteString]("ByteStringConcat.out")
    val shape  = FlowShape(inlet, outlet)

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
          val size      = math.max(0L, math.min(limit - written, bytes.length)).toInt
          val truncated = bytes.take(size)
          written += size
          push(outlet, truncated)
          if (isLimitReached(written + 1)) completeStage()
        } else if (isLimitReached(written + bytes.length)) {
          failStage(new IOException(s"Write limit reached: ${MemorySize(limit)}"))
        } else {
          written += bytes.length
          push(outlet, bytes)
        }
      }

      setHandlers(inlet, outlet, this)
    }
  }

  private[this] final class BytesBuffer[T](getSize: T ⇒ Long, maxSize: Long) extends GraphStage[FlowShape[T, T]] {
    val inlet  = Inlet[T]("BytesBuffer.in")
    val outlet = Outlet[T]("BytesBuffer.out")
    val shape  = FlowShape(inlet, outlet)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler with StageLogging {
        override protected def logSource: Class[_] = classOf[BytesBuffer[_]]

        private[this] var buffer = ListBuffer.empty[T]
        def currentSize: Long    = buffer.map(getSize).sum

        def enqueue(e: T): Unit = {
          buffer += e
          if (currentSize < maxSize) pull(inlet)
        }

        override def preStart(): Unit = {
          pull(inlet)
        }

        override def onPush(): Unit = {
          val elem = grab(inlet)
          if (isAvailable(outlet)) {
            push(outlet, elem)
            pull(inlet)
          } else {
            enqueue(elem)
          }
          log.debug("Buffer size: {} MB of {} MB", currentSize / SizeUnit.MB, maxSize / SizeUnit.MB)
        }

        override def onPull(): Unit = {
          if (buffer.nonEmpty) {
            val element = buffer.remove(0)
            push(outlet, element)
          }

          if (isClosed(inlet)) {
            if (buffer.isEmpty) completeStage()
          } else if (!hasBeenPulled(inlet)) {
            pull(inlet)
          }
        }

        override def onUpstreamFinish(): Unit = {
          if (buffer.isEmpty) completeStage()
        }

        setHandlers(inlet, outlet, this)
      }
  }
}
