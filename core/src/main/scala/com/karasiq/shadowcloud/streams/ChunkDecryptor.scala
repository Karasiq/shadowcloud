package com.karasiq.shadowcloud.streams

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule}
import com.karasiq.shadowcloud.index.Chunk

import scala.collection.mutable
import scala.language.postfixOps

object ChunkDecryptor {
  def apply(): ChunkDecryptor = {
    new ChunkDecryptor()
  }
}

final class ChunkDecryptor extends GraphStage[FlowShape[Chunk, Chunk]] {
  val inlet = Inlet[Chunk]("ChunkDecryptor.in")
  val outlet = Outlet[Chunk]("ChunkDecryptor.out")
  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    val decryptors = mutable.AnyRefMap[EncryptionMethod, EncryptionModule]()

    private[this] def decryptChunk(chunk: Chunk): Chunk = {
      val decryptor = decryptors.getOrElseUpdate(chunk.encryption.method, EncryptionModule(chunk.encryption.method))
      val decryptedData = decryptor.decrypt(chunk.data.encrypted, chunk.encryption)
      chunk.copy(data = chunk.data.copy(plain = decryptedData))
    }

    def onPull(): Unit = {
      tryPull(inlet)
    }

    def onPush(): Unit = {
      val chunk = grab(inlet)
      val decryptedChunk = decryptChunk(chunk)
      push(outlet, decryptedChunk)
    }

    setHandlers(inlet, outlet, this)
  }
}
