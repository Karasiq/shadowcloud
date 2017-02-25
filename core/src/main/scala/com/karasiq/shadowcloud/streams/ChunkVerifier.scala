package com.karasiq.shadowcloud.streams

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule}
import com.karasiq.shadowcloud.index.Chunk

import scala.collection.mutable
import scala.language.postfixOps

object ChunkVerifier {
  def apply(): ChunkVerifier = {
    new ChunkVerifier()
  }
}

final class ChunkVerifier extends GraphStage[FlowShape[Chunk, Chunk]] {
  val inlet = Inlet[Chunk]("ChunkVerifier.in")
  val outlet = Outlet[Chunk]("ChunkVerifier.out")
  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] val hashers = mutable.AnyRefMap[HashingMethod, HashingModule]()

    def onPull(): Unit = {
      tryPull(inlet)
    }

    def onPush(): Unit = {
      val chunk = grab(inlet)
      val hasher = hashers.getOrElseUpdate(chunk.checksum.method, HashingModule(chunk.checksum.method))
      if (chunk.checksum.hash.nonEmpty && hasher.createHash(chunk.data.plain) != chunk.checksum.hash) {
        failStage(new IllegalArgumentException(s"Chunk plaintext checksum not match: $chunk"))
      } else if (chunk.checksum.encryptedHash.nonEmpty && hasher.createHash(chunk.data.encrypted) != chunk.checksum.encryptedHash) {
        failStage(new IllegalArgumentException(s"Chunk ciphertext checksum not match: $chunk"))
      } else if ((chunk.data.plain.nonEmpty && chunk.checksum.size != chunk.data.plain.length) ||
        (chunk.data.encrypted.nonEmpty && chunk.checksum.encryptedSize != chunk.data.encrypted.length)) {
        failStage(new IllegalArgumentException(s"Chunk sizes not match: $chunk"))
      } else {
        push(outlet, chunk)
      }
    }

    setHandlers(inlet, outlet, this)
  }
}
