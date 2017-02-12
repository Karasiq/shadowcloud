package com.karasiq.shadowcloud.streams

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule}
import com.karasiq.shadowcloud.index.Chunk

import scala.collection.mutable
import scala.language.postfixOps

class ChunkDecryptor extends GraphStage[FlowShape[Chunk, Chunk]] {
  val inlet = Inlet[Chunk]("ChunkDecryptor.in")
  val outlet = Outlet[Chunk]("ChunkDecryptor.out")

  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
    val decryptors = mutable.AnyRefMap[EncryptionMethod, EncryptionModule]()

    setHandler(inlet, new InHandler {
      def onPush(): Unit = {
        val chunk = grab(inlet)
        val decryptor = decryptors.getOrElseUpdate(chunk.encryption.method, EncryptionModule(chunk.encryption.method))
        val plain = decryptor.decrypt(chunk.data.encrypted, chunk.encryption)
        val decryptedChunk = chunk.copy(data = chunk.data.copy(plain = plain))
        emit(outlet, decryptedChunk)
      }
    })

    setHandler(outlet, new OutHandler {
      def onPull(): Unit = {
        tryPull(inlet)
      }
    })
  }
}
