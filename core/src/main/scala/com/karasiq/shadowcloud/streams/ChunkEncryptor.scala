package com.karasiq.shadowcloud.streams

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod, HashingModule}
import com.karasiq.shadowcloud.index.Chunk

import scala.language.postfixOps
import scala.util.Random

object ChunkEncryptor {
  def apply(encryptionMethod: EncryptionMethod = EncryptionMethod.default, hashingMethod: HashingMethod = HashingMethod.default): ChunkEncryptor = {
    new ChunkEncryptor(encryptionMethod, hashingMethod)
  }
}

final class ChunkEncryptor(encryptionMethod: EncryptionMethod, hashingMethod: HashingMethod) extends GraphStage[FlowShape[Chunk, Chunk]] {
  val inlet = Inlet[Chunk]("ChunkEncryptor.in")
  val outlet = Outlet[Chunk]("ChunkEncryptor.out")
  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] val encryptionModule = EncryptionModule(encryptionMethod)
    private[this] val hashingModule = HashingModule(hashingMethod)
    private[this] var keyParameters = encryptionModule.createParameters()
    private[this] var encryptedCount = 0
    private[this] var changeKeyIn = Random.nextInt(256)

    // Encrypt chunk
    private[this] def encryptChunk(chunk: Chunk): Chunk = {
      require(chunk.checksum.method == hashingModule.method)
      val encryptedData = encryptionModule.encrypt(chunk.data.plain, keyParameters)
      val encryptedHash = hashingModule.createHash(encryptedData)
      chunk.copy(
        checksum = chunk.checksum.copy(encryptedSize = encryptedData.length, encryptedHash = encryptedHash),
        encryption = keyParameters,
        data = chunk.data.copy(encrypted = encryptedData)
      )
    }

    // Update IV/key
    private[this] def updateKey(): Unit = {
      encryptedCount += 1
      if (encryptedCount > changeKeyIn) {
        // TODO: Log key changes
        keyParameters = encryptionModule.createParameters()
        changeKeyIn = Random.nextInt(256)
        encryptedCount = 0
      } else {
        keyParameters = encryptionModule.updateParameters(keyParameters)
      }
    }

    def onPull(): Unit = {
      tryPull(inlet)
    }

    def onPush(): Unit = {
      val chunk = grab(inlet)
      val encryptedChunk = encryptChunk(chunk)
      updateKey()
      push(outlet, encryptedChunk)
    }

    setHandlers(inlet, outlet, this)
  }
}