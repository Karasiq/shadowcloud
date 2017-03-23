package com.karasiq.shadowcloud.streams

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.{Chunk, Data}
import com.karasiq.shadowcloud.utils.MemorySize

import scala.language.postfixOps

private[shadowcloud] object ChunkSplitter {
  def apply(chunkSize: Int = MemorySize.MB): Flow[ByteString, Chunk, NotUsed] = {
    Flow.fromGraph(new ChunkSplitter(chunkSize))
  }
}

/**
  * Splits input data to fixed size chunks
  * @param chunkSize Output chunk size
  */
private[shadowcloud] final class ChunkSplitter(chunkSize: Int) extends GraphStage[FlowShape[ByteString, Chunk]] {
  require(chunkSize > 0)
  val inBytes = Inlet[ByteString]("FileSplitter.inBytes")
  val outChunks = Outlet[Chunk]("FileSplitter.outChunks")
  val shape = FlowShape(inBytes, outChunks)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] var buffer = ByteString.empty

    private[this] def emitNextChunk(after: () ⇒ Unit = () ⇒ ()): Unit = {
      require(buffer.nonEmpty, "Buffer is empty")
      val (chunkBytes, nextChunkPart) = buffer.splitAt(chunkSize)

      // Create new chunk
      val chunk = Chunk(data = Data(plain = chunkBytes.compact))

      // Reset buffer
      buffer = nextChunkPart

      // Emit chunk
      emit(outChunks, chunk, after)
    }

    private[this] def emitLastChunk(): Unit = {
      if (buffer.nonEmpty) {
        emitNextChunk(emitLastChunk)
      } else {
        complete(outChunks)
      }
    }

    private[this] def emitOrPullBytes(): Unit = {
      if (buffer.length >= chunkSize) { // Emit finished chunk
        emitNextChunk()
      } else if (isClosed(inBytes)) { // Emit last part and close stream
        emitLastChunk()
      } else { // Pull more bytes
        pull(inBytes)
      }
    }

    def onPull(): Unit = {
      emitOrPullBytes()
    }

    def onPush(): Unit = {
      val bytes = grab(inBytes)
      buffer ++= bytes
      emitOrPullBytes()
    }

    override def onUpstreamFinish(): Unit = {
      emitLastChunk()
    }

    setHandlers(inBytes, outChunks, this)
  }
}
