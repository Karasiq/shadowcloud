package com.karasiq.shadowcloud.streams

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule}
import com.karasiq.shadowcloud.index.{Checksum, Chunk, Data}

import scala.language.postfixOps

/**
  * Splits input data to fixed size chunks with hash
  * @param chunkSize Output chunk size
  * @param hashingMethod Hashing method
  */
class FileSplitter(chunkSize: Int, hashingMethod: HashingMethod) extends GraphStage[FlowShape[ByteString, Chunk]] {
  val inBytes = Inlet[ByteString]("FileSplitter.inBytes")
  val outChunks = Outlet[Chunk]("FileSplitter.outChunks")
  val shape = FlowShape(inBytes, outChunks)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] val hashingModule = HashingModule(hashingMethod)
    private[this] var buffer = ByteString.empty

    private[this] def hashAndWrite(bytes: ByteString): Unit = {
      val sizeToHash = chunkSize - buffer.length
      if (sizeToHash > 0) {
        val bytesToHash = bytes.take(sizeToHash)
        hashingModule.update(bytesToHash)
      }
      buffer ++= bytes
    }

    private[this] def emitNextChunk(after: () ⇒ Unit = () ⇒ ()): Unit = {
      require(buffer.nonEmpty, "Buffer is empty")
      val (chunkBytes, nextChunkPart) = buffer.splitAt(chunkSize)

      // Create new chunk
      val chunk = Chunk(checksum = Checksum(hashingModule.method, chunkBytes.size, hashingModule.createHash()), data = Data(plain = chunkBytes))

      // Reset buffer
      buffer = ByteString.empty
      hashingModule.reset()
      if (nextChunkPart.nonEmpty) hashAndWrite(nextChunkPart)

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
      if (bytes.nonEmpty) hashAndWrite(bytes)
      emitOrPullBytes()
    }

    override def onUpstreamFinish(): Unit = {
      emitLastChunk()
    }

    setHandlers(inBytes, outChunks, this)
  }
}
