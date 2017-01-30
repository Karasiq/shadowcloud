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

    def hashBytes(bytes: ByteString): Unit = {
      val size = chunkSize - buffer.length
      if (size > 0) {
        val array = bytes.take(size).toArray
        messageDigest.update(array)
      }
      buffer ++= bytes
    }

    def emitBuffer(): Unit = {
      require(buffer.nonEmpty, "Buffer is empty")
      val (drop, keep) = buffer.splitAt(chunkSize)
      val newChunk = Chunk(chunkSize, ByteString(messageDigest.digest()), ByteString.empty, drop)
      emit(outChunks, newChunk, () ⇒ {
        buffer = ByteString.empty
        messageDigest.reset()
        if (keep.nonEmpty) hashBytes(keep)

        if (buffer.length >= chunkSize) emitBuffer()
        else {
          if (isClosed(inBytes)) {
            if (buffer.nonEmpty) emitBuffer() else complete(outChunks)
          } else {
            pull(inBytes)
          }
        }
      })
    }

    setHandler(inBytes, new InHandler {
      def onPush() = {
        val bytes = grab(inBytes)
        if (bytes.nonEmpty) hashBytes(bytes)
        if (buffer.length >= chunkSize) emitBuffer()
        else tryPull(inBytes)
      }

      @scala.throws[Exception](classOf[Exception])
      override def onUpstreamFinish() = {
        if (buffer.isEmpty) complete(outChunks)
        else emitBuffer()
      }
    })

    setHandler(outChunks, new OutHandler {
      def onPull() = {
        tryPull(inBytes)
        setHandler(outChunks, eagerTerminateOutput)
      }
    })
  }
}
