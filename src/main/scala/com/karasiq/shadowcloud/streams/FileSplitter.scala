package com.karasiq.shadowcloud.streams

import java.security.MessageDigest

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk

import scala.language.postfixOps

/**
  * Splits input data to fixed size chunks with hash
  * @param chunkSize Output chunk size
  * @param hashAlg Hashing algorithm
  */
class FileSplitter(chunkSize: Int, hashAlg: String) extends GraphStage[FlowShape[ByteString, Chunk]] {
  val inBytes = Inlet[ByteString]("FileSplitter.inBytes")
  val outChunks = Outlet[Chunk]("FileSplitter.outChunks")
  val shape = FlowShape(inBytes, outChunks)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
    val chunkHash = MessageDigest.getInstance(hashAlg)
    var buffer = ByteString.empty

    def hashAndWrite(bytes: ByteString): Unit = {
      val sizeToHash = chunkSize - buffer.length
      if (sizeToHash > 0) {
        val bytesToHash = bytes.take(sizeToHash).toArray
        chunkHash.update(bytesToHash)
      }
      buffer ++= bytes
    }

    def emitNextChunk(): Unit = {
      require(buffer.nonEmpty, "Buffer is empty")
      val (chunkBytes, nextChunkPart) = buffer.splitAt(chunkSize)

      // Create new chunk
      val chunk = Chunk(chunkBytes.size, ByteString(chunkHash.digest()), ByteString.empty, chunkBytes)

      // Reset buffer
      buffer = ByteString.empty
      chunkHash.reset()
      if (nextChunkPart.nonEmpty) hashAndWrite(nextChunkPart)

      // Emit chunk
      emit(outChunks, chunk, processBuffer _)
    }

    def emitLastChunk(): Unit = {
      if (buffer.nonEmpty)
        emitNextChunk()
      else
        complete(outChunks)
    }

    def processBuffer(): Unit = {
      if (buffer.length >= chunkSize) { // Emit finished chunk
        emitNextChunk()
      } else if (isClosed(inBytes)) { // Emit last part and close stream
        emitLastChunk()
      } else { // Pull more bytes
        pull(inBytes)
      }
    }

    setHandler(inBytes, new InHandler {
      def onPush() = {
        val bytes = grab(inBytes)
        if (bytes.nonEmpty) hashAndWrite(bytes)
        processBuffer()
      }

      @scala.throws[Exception](classOf[Exception])
      override def onUpstreamFinish() = {
        emitLastChunk()
      }
    })

    setHandler(outChunks, new OutHandler {
      def onPull() = {
        if (isClosed(inBytes)) {
          complete(outChunks)
        } else {
          pull(inBytes)
          setHandler(outChunks, eagerTerminateOutput)
        }
      }
    })
  }
}
