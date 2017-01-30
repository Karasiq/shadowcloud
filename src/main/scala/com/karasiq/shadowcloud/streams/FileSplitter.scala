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
    val messageDigest = MessageDigest.getInstance(hashAlg)
    var buffer = ByteString.empty

    def hashAndWrite(bytes: ByteString): Unit = {
      val size = chunkSize - buffer.length
      if (size > 0) {
        val array = bytes.take(size).toArray
        messageDigest.update(array)
      }
      buffer ++= bytes
    }

    def emitNextChunk(): Unit = {
      require(buffer.nonEmpty, "Buffer is empty")
      val (drop, keep) = buffer.splitAt(chunkSize)

      // Create new chunk
      val newChunk = Chunk(chunkSize, ByteString(messageDigest.digest()), ByteString.empty, drop)

      // Reset buffer
      buffer = ByteString.empty
      messageDigest.reset()
      if (keep.nonEmpty) hashAndWrite(keep)

      // Emit chunk
      emit(outChunks, newChunk, processBuffer _)
    }

    def emitLastChunk(): Unit = {
      if (buffer.nonEmpty) emitNextChunk() else complete(outChunks)
    }

    def processBuffer(): Unit = {
      if (buffer.length >= chunkSize) {
        emitNextChunk()
      } else if (isClosed(inBytes)) {
        emitLastChunk()
      } else {
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
